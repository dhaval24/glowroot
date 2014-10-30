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
package org.glowroot.advicegen;

import org.junit.Test;

import org.glowroot.api.Message;
import org.glowroot.api.internal.ReadableMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
public class GenericMessageSupplierTest {

    @Test
    public void shouldRenderConstant() {
        MessageTemplate template = MessageTemplate.create("abc", TestReceiver.class, void.class,
                new Class<?>[] {HasName.class});
        Message message = GenericMessageSupplier.create(template, new HasName(), "execute",
                new HasName()).get();
        String text = ((ReadableMessage) message).getText();
        assertThat(text).isEqualTo("abc");
    }

    @Test
    public void shouldRenderNormal() {
        MessageTemplate template = MessageTemplate.create(
                "{{this.class.name}}.{{methodName}}(): {{0.name}} => {{_}}", TestReceiver.class,
                void.class, new Class<?>[] {HasName.class});
        Message message = GenericMessageSupplier.create(template, new TestReceiver(), "execute",
                new HasName()).get();
        String text = ((ReadableMessage) message).getText();
        assertThat(text).isEqualTo(TestReceiver.class.getName() + ".execute(): the name => ");
    }

    @Test
    public void shouldRenderTrailingText() {
        MessageTemplate template = MessageTemplate.create(
                "{{this.class.name}}.{{methodName}}(): {{0.name}} trailing", TestReceiver.class,
                void.class, new Class<?>[] {HasName.class});
        Message message = GenericMessageSupplier.create(template, new TestReceiver(), "execute",
                new HasName()).get();
        String text = ((ReadableMessage) message).getText();
        assertThat(text).isEqualTo(TestReceiver.class.getName() + ".execute(): the name trailing");
    }

    @Test
    public void shouldRenderBadText() {
        MessageTemplate template = MessageTemplate.create(
                "{{this.class.name}}.{{methodName}}(): {{1.name}} trailing", TestReceiver.class,
                void.class, new Class<?>[] {HasName.class});
        Message message = GenericMessageSupplier.create(template, new TestReceiver(), "execute",
                new HasName()).get();
        String text = ((ReadableMessage) message).getText();
        assertThat(text).isEqualTo(TestReceiver.class.getName()
                + ".execute(): <requested arg index out of bounds: 1> trailing");
    }

    @Test
    public void shouldRenderArray() {
        MessageTemplate template = MessageTemplate.create(
                "{{this.class.name}}.{{methodName}}(): {{0.names}}", TestReceiver.class,
                void.class, new Class<?>[] {HasArray.class});
        Message message = GenericMessageSupplier.create(template, new TestReceiver(), "execute",
                new HasArray()).get();
        String text = ((ReadableMessage) message).getText();
        assertThat(text).isEqualTo(
                TestReceiver.class.getName() + ".execute(): [the name, two]");
    }

    @Test
    public void shouldRenderArray1() {
        MessageTemplate template = MessageTemplate.create(
                "{{this.class.name}}.{{methodName}}(): {{0.names.name}}",
                TestReceiver.class,
                void.class, new Class<?>[] {HasArray1.class});
        Message message = GenericMessageSupplier.create(template, new TestReceiver(), "execute",
                new HasArray1()).get();
        String text = ((ReadableMessage) message).getText();
        assertThat(text).isEqualTo(
                TestReceiver.class.getName() + ".execute(): [the name, the name]");
    }

    @Test
    public void shouldRenderArray2() {
        MessageTemplate template = MessageTemplate.create(
                "{{this.class.name}}.{{methodName}}(): {{0.names.name}}",
                TestReceiver.class,
                void.class, new Class<?>[] {HasArray2.class});
        Message message = GenericMessageSupplier.create(template, new TestReceiver(), "execute",
                new HasArray2()).get();
        String text = ((ReadableMessage) message).getText();
        assertThat(text).isEqualTo(
                TestReceiver.class.getName() + ".execute(): [[the name, the name]]");
    }

    @Test
    public void shouldRenderArrayLength() {
        MessageTemplate template = MessageTemplate.create(
                "{{this.class.name}}.{{methodName}}(): {{0.names.length}}", TestReceiver.class,
                void.class, new Class<?>[] {HasArray.class});
        Message message = GenericMessageSupplier.create(template, new TestReceiver(), "execute",
                new HasArray()).get();
        String text = ((ReadableMessage) message).getText();
        assertThat(text).isEqualTo(
                TestReceiver.class.getName() + ".execute(): 2");
    }

    public static class HasName {
        public String getName() {
            return "the name";
        }
    }

    public static class HasArray {
        public String[] getNames() {
            return new String[] {"the name", "two"};
        }
    }

    public static class HasArray1 {
        public HasName[] getNames() {
            return new HasName[] {new HasName(), new HasName()};
        }
    }

    public static class HasArray2 {
        public HasName[][] getNames() {
            return new HasName[][] {new HasName[] {new HasName(), new HasName()}};
        }
    }

    public static class TestReceiver {
        public void execute(@SuppressWarnings("unused") HasName arg) {}
    }
}
