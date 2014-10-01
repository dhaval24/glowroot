/*
 * Copyright 2013-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.local.ui;

import java.io.IOException;
import java.net.URL;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
class IndexHtmlHttpService implements HttpHandler {

    private final HttpSessionManager httpSessionManager;
    private final LayoutJsonService layoutJsonService;

    IndexHtmlHttpService(HttpSessionManager httpSessionManager,
            LayoutJsonService layoutJsonService) {
        this.httpSessionManager = httpSessionManager;
        this.layoutJsonService = layoutJsonService;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws IOException {
        URL url = Resources.getResource("org/glowroot/local/ui/app-dist/index.html");
        String indexHtml = Resources.toString(url, Charsets.UTF_8);
        String layout;
        if (httpSessionManager.needsAuthentication(exchange)) {
            layout = layoutJsonService.getUnauthenticatedLayout();
        } else {
            layout = layoutJsonService.getLayout();
        }
        String baseHrefScript = "var path=location.pathname;";
        if (exchange.getRequestPath().equals("/")) {
            // edge case, if request uri is "/", the location.pathname may end with "/" or not
            baseHrefScript += "if(!path||path.slice(-1)!=='/')path+='/';";
        }
        baseHrefScript += "var base=path.substring(0,path.length-"
                + exchange.getRequestPath().length() + ");"
                + "document.write('<base href=\"'+location.protocol+'//'"
                + "+location.host+base+'/\"/>');";
        // embed script in IIFE to not polute global vars
        baseHrefScript = "(function(){" + baseHrefScript + "}());";
        String layoutScript = "var layout=" + layout + ";";
        indexHtml = indexHtml.replaceFirst("<base href=\"/\">", "<script>" + baseHrefScript
                + layoutScript + "</script>");
        // this is to work around an issue with Chrome when running behind reverse proxy with
        // non-root base href, e.g. /glowroot
        // the issue is that Chrome doesn't use the custom base href when loading the final script
        // tag on the page
        indexHtml = indexHtml.replaceFirst(
                "<script src=\"scripts/scripts\\.([0-9a-f]+)\\.js\"></script>",
                "<script>document.write('<script src=\"'"
                        + " + document.getElementsByTagName(\"base\")[0].href"
                        + " + 'scripts/scripts.$1.js\"><\\\\/script>');</script>");
        // this is to work around an issue with IE10-11 (IE9 is OK)
        // (even without reverse proxy/non-root base href)
        // IE doesn't use the base href when loading the favicon
        indexHtml = indexHtml.replaceFirst(
                "<link rel=\"shortcut icon\" href=\"favicon\\.([0-9a-f]+)\\.ico\">",
                "<script>document.write('<link rel=\"shortcut icon\" href=\"'"
                        + " + document.getElementsByTagName(\"base\")[0].href"
                        + " + 'favicon.$1.ico\">');</script>");
        HttpServices.preventCaching(exchange);
        exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, "text/html; charset=UTF-8");
        exchange.getResponseHeaders().add(Headers.CONTENT_LENGTH, indexHtml.length());
        // X-UA-Compatible must be set via header (as opposed to via meta tag)
        // see https://github.com/h5bp/html5-boilerplate/blob/master/doc/html.md#x-ua-compatible
        exchange.getResponseHeaders().add(HttpString.tryFromString("X-UA-Compatible"), "IE=edge");
        exchange.getResponseSender().send(indexHtml);
    }
}
