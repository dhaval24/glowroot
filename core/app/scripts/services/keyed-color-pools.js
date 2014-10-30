/*
 * Copyright 2014 the original author or authors.
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

/* global glowroot, angular, $ */

glowroot.factory('keyedColorPools', [
  function () {
    var fixedPool = ['#edc240', '#afd8f8', '#cb4b4b', '#4da74d', '#9440ed'];
    var dynamicPool = fixedPool.slice(0);
    var variation = 0;

    function getColor(i) {
      if (i < dynamicPool.length) {
        return dynamicPool[i];
      }
      // this color generation code is copied from jquery.flot.js
      var c = $.color.parse(fixedPool[i % fixedPool.length] || '#666');
      if (i % fixedPool.length === 0 && i) {
        if (variation >= 0) {
          if (variation < 0.5) {
            variation = -variation - 0.2;
          } else {
            variation = 0;
          }
        } else {
          variation = -variation;
        }
      }
      var color = c.scale('rgb', 1 + variation);
      dynamicPool.push(color);
      return color;
    }

    function create() {
      var colors = {};
      var used = {};

      function nextAvailable() {
        for (var i = 0; i < dynamicPool.length; i++) {
          var color = dynamicPool[i];
          if (!used[color]) {
            return color;
          }
        }
        return getColor(dynamicPool.length);
      }

      return {
        reset: function (keys) {
          var preservedColors = {};
          used = {};
          angular.forEach(keys, function (key) {
            var color = colors[key];
            if (color) {
              preservedColors[key] = color;
              used[color] = true;
            }
          });
          colors = preservedColors;
          angular.forEach(keys, function (key) {
            if (!colors[key]) {
              var color = nextAvailable();
              colors[key] = color;
              used[color] = true;
            }
          });
        },
        keys: function () {
          return Object.keys(colors);
        },
        add: function (key) {
          var color = colors[key];
          if (color) {
            return color;
          }
          color = nextAvailable();
          colors[key] = color;
          used[color] = true;
          return color;
        },
        get: function (key) {
          return colors[key];
        },
        remove: function (key) {
          var color = colors[key];
          if (color) {
            delete colors[key];
            delete used[color];
          }
        }
      };
    }

    return {
      create: create
    };
  }
]);
