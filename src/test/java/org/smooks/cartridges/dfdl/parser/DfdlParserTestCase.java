/*-
 * ========================LICENSE_START=================================
 * Smooks DFDL Cartridge
 * %%
 * Copyright (C) 2020 Smooks
 * %%
 * Licensed under the terms of the Apache License Version 2.0, or
 * the GNU Lesser General Public License version 3.0 or later.
 *
 * SPDX-License-Identifier: Apache-2.0 OR LGPL-3.0-or-later
 *
 * ======================================================================
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ======================================================================
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 * =========================LICENSE_END==================================
 */
package org.smooks.cartridges.dfdl.parser;

import org.apache.daffodil.japi.DataProcessor;
import org.apache.daffodil.japi.Diagnostic;
import org.apache.daffodil.japi.ParseResult;
import org.apache.daffodil.japi.ValidationMode;
import org.apache.daffodil.japi.infoset.InfosetOutputter;
import org.apache.daffodil.japi.io.InputSourceDataInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.smooks.Smooks;
import org.smooks.api.ExecutionContext;
import org.smooks.api.SmooksException;
import org.smooks.api.resource.config.ResourceConfig;
import org.smooks.cartridges.dfdl.AbstractTestCase;
import org.smooks.cartridges.dfdl.DataProcessorFactory;
import org.smooks.engine.DefaultApplicationContextBuilder;
import org.smooks.engine.delivery.sax.ng.SaxNgContentHandler;
import org.smooks.engine.resource.config.DefaultResourceConfig;
import org.smooks.engine.xml.NamespaceManager;
import org.smooks.io.Stream;
import org.smooks.namespace.NamespaceDeclarationStack;
import org.smooks.support.StreamUtils;
import org.smooks.testkit.MockApplicationContext;
import org.smooks.testkit.MockExecutionContext;
import org.smooks.testkit.TextUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DfdlParserTestCase extends AbstractTestCase {

    private StringWriter stringWriter;
    private SaxNgContentHandler saxContentHandler;

    @BeforeEach
    public void beforeEach() throws ParserConfigurationException {
        ExecutionContext executionContext = new Smooks(new DefaultApplicationContextBuilder().withClassLoader(this.getClass().getClassLoader()).build()).createExecutionContext();
        executionContext.put(NamespaceManager.NAMESPACE_DECLARATION_STACK_TYPED_KEY, new NamespaceDeclarationStack());
        stringWriter = new StringWriter();
        executionContext.put(Stream.STREAM_WRITER_TYPED_KEY, stringWriter);
        saxContentHandler = new SaxNgContentHandler(executionContext, DocumentBuilderFactory.newInstance().newDocumentBuilder());
    }

    public static class DiagnosticErrorDataProcessorFactory extends DataProcessorFactory {

        public DiagnosticErrorDataProcessorFactory() {

        }

        @Override
        public DataProcessor createDataProcessor() {
            return new DataProcessor(null) {
                @Override
                public DataProcessor withExternalVariables(AbstractMap<String, String> extVars) {
                    return this;
                }

                @Override
                public ParseResult parse(InputSourceDataInputStream input, InfosetOutputter output) {
                    return new ParseResult(null) {

                        @Override
                        public boolean isError() {
                            return false;
                        }

                        @Override
                        public List<Diagnostic> getDiagnostics() {
                            return Collections.singletonList(new Diagnostic(null) {
                                @Override
                                public String getSomeMessage() {
                                    return "";
                                }

                                @Override
                                public Throwable getSomeCause() {
                                    return new Throwable();
                                }

                                @Override
                                public boolean isError() {
                                    return true;
                                }
                            });
                        }
                    };
                }
            };
        }
    }

    @ParameterizedTest
    @EnumSource(value = ValidationMode.class)
    public void testParseWhenParseError(ValidationMode validationMode) throws Exception {
        ResourceConfig resourceConfig = new DefaultResourceConfig();
        resourceConfig.setParameter("schemaUri", "/csv.dfdl.xsd");

        DfdlParser dfdlParser = new DfdlParser();
        dfdlParser.setDataProcessorFactoryClass(DataProcessorFactory.class);
        dfdlParser.setResourceConfig(resourceConfig);
        dfdlParser.setExecutionContext(new MockExecutionContext());
        dfdlParser.setApplicationContext(new MockApplicationContext());
        dfdlParser.setContentHandler(saxContentHandler);
        dfdlParser.setValidationMode(validationMode);

        dfdlParser.postConstruct();

        assertThrows(ParserDfdlSmooksException.class, () -> dfdlParser.parse(new InputSource(new ByteArrayInputStream("foo".getBytes()))));
    }

    @Test
    public void testParseWhenDiagnosticExistsButNotParseError() throws Exception {
        ResourceConfig resourceConfig = new DefaultResourceConfig();
        resourceConfig.setParameter("schemaUri", "");

        DfdlParser dfdlParser = new DfdlParser();
        dfdlParser.setDataProcessorFactoryClass(DiagnosticErrorDataProcessorFactory.class);
        dfdlParser.setResourceConfig(resourceConfig);
        dfdlParser.setApplicationContext(new MockApplicationContext());
        dfdlParser.setContentHandler(saxContentHandler);
        dfdlParser.setExecutionContext(new MockExecutionContext());

        dfdlParser.postConstruct();
        dfdlParser.parse(new InputSource(new ByteArrayInputStream("".getBytes())));

        assertEquals("", stringWriter.toString());
    }

    @Test
    public void testParseWhenContentHandlerError() throws Exception {
        ResourceConfig resourceConfig = new DefaultResourceConfig();
        resourceConfig.setParameter("schemaUri", "/csv.dfdl.xsd");

        DfdlParser dfdlParser = new DfdlParser();
        dfdlParser.setDataProcessorFactoryClass(DataProcessorFactory.class);
        dfdlParser.setResourceConfig(resourceConfig);
        dfdlParser.setApplicationContext(new MockApplicationContext());
        dfdlParser.setIndent(true);
        dfdlParser.setContentHandler(new ContentHandler() {
            @Override
            public void setDocumentLocator(Locator locator) {

            }

            @Override
            public void startDocument() throws SAXException {

            }

            @Override
            public void endDocument() throws SAXException {

            }

            @Override
            public void startPrefixMapping(String prefix, String uri) throws SAXException {

            }

            @Override
            public void endPrefixMapping(String prefix) throws SAXException {

            }

            @Override
            public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
                throw new SmooksException("ContentHandler error");
            }

            @Override
            public void endElement(String uri, String localName, String qName) throws SAXException {

            }

            @Override
            public void characters(char[] ch, int start, int length) throws SAXException {

            }

            @Override
            public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {

            }

            @Override
            public void processingInstruction(String target, String data) throws SAXException {

            }

            @Override
            public void skippedEntity(String name) throws SAXException {

            }
        });

        dfdlParser.postConstruct();
        SmooksException smooksException = assertThrows(SmooksException.class, () -> dfdlParser.parse(new InputSource(getClass().getResourceAsStream("/data/simpleCSV.comma.csv"))));
        assertEquals("org.smooks.api.SmooksException: ContentHandler error", smooksException.getMessage());
    }

    @Test
    public void testParse() throws Exception {
        ResourceConfig resourceConfig = new DefaultResourceConfig();
        resourceConfig.setParameter("schemaUri", "/csv.dfdl.xsd");

        DfdlParser dfdlParser = new DfdlParser();
        dfdlParser.setDataProcessorFactoryClass(DataProcessorFactory.class);
        dfdlParser.setResourceConfig(resourceConfig);
        dfdlParser.setApplicationContext(new MockApplicationContext());
        dfdlParser.setIndent(true);
        dfdlParser.setContentHandler(saxContentHandler);

        dfdlParser.postConstruct();
        dfdlParser.parse(new InputSource(getClass().getResourceAsStream("/data/simpleCSV.comma.csv")));

        assertEquals(StreamUtils.readStreamAsString(getClass().getResourceAsStream("/data/simpleCSV.xml"), "UTF-8"), stringWriter.toString());
    }

    @Test
    public void testParseGivenInputSourceIsReader() throws Exception {
        ResourceConfig resourceConfig = new DefaultResourceConfig();
        resourceConfig.setParameter("schemaUri", "/csv.dfdl.xsd");

        DfdlParser dfdlParser = new DfdlParser();
        dfdlParser.setDataProcessorFactoryClass(DataProcessorFactory.class);
        dfdlParser.setResourceConfig(resourceConfig);
        dfdlParser.setApplicationContext(new MockApplicationContext());
        dfdlParser.setIndent(true);
        dfdlParser.setContentHandler(saxContentHandler);
        dfdlParser.setExecutionContext(new MockExecutionContext());

        dfdlParser.postConstruct();
        dfdlParser.parse(new InputSource(new InputStreamReader(getClass().getResourceAsStream("/data/simpleCSV.comma.csv"), "UTF-8")));

        assertEquals(StreamUtils.readStreamAsString(getClass().getResourceAsStream("/data/simpleCSV.xml"), "UTF-8"), stringWriter.toString());
    }

    @Test
    public void testIncrementalParse() throws Exception {
        ResourceConfig resourceConfig = new DefaultResourceConfig();
        resourceConfig.setParameter("schemaUri", "/csv.dfdl.xsd");

        DfdlParser dfdlParser = new DfdlParser();
        dfdlParser.setDataProcessorFactoryClass(DataProcessorFactory.class);
        dfdlParser.setResourceConfig(resourceConfig);
        dfdlParser.setApplicationContext(new MockApplicationContext());
        dfdlParser.setIndent(true);
        dfdlParser.setContentHandler(saxContentHandler);
        dfdlParser.postConstruct();

        String input = StreamUtils.readStreamAsString(getClass().getResourceAsStream("/data/simpleCSV.comma.csv"), "UTF-8");
        dfdlParser.parse(new InputSource(new ByteArrayInputStream((input + input).getBytes())));

        String expectedResult = StreamUtils.readStreamAsString(getClass().getResourceAsStream("/data/simpleCSV.xml"), "UTF-8");
        assertEquals(expectedResult + expectedResult, stringWriter.toString());
    }

    @Test
    public void testParseGivenIndentIsFalse() throws Exception {
        ResourceConfig resourceConfig = new DefaultResourceConfig();
        resourceConfig.setParameter("schemaUri", "/csv.dfdl.xsd");

        DfdlParser dfdlParser = new DfdlParser();
        dfdlParser.setDataProcessorFactoryClass(DataProcessorFactory.class);
        dfdlParser.setResourceConfig(resourceConfig);
        dfdlParser.setApplicationContext(new MockApplicationContext());
        dfdlParser.setIndent(false);
        dfdlParser.setContentHandler(saxContentHandler);

        dfdlParser.postConstruct();
        dfdlParser.parse(new InputSource(getClass().getResourceAsStream("/data/simpleCSV.comma.csv")));

        assertEquals(TextUtils.trimLines(StreamUtils.readStreamAsString(getClass().getResourceAsStream("/data/simpleCSV.xml"), "UTF-8")), stringWriter.toString());
    }

    @Test
    public void testParseGivenNonUtf8CharacterStreamInputSource() throws Exception {
        ResourceConfig resourceConfig = new DefaultResourceConfig();
        resourceConfig.setParameter("schemaUri", "/csv.dfdl.xsd");
        resourceConfig.setParameter("variables",  new AbstractMap.SimpleEntry<>("Encoding", "ISO-8859-1"));

        DfdlParser dfdlParser = new DfdlParser();
        dfdlParser.setDataProcessorFactoryClass(DataProcessorFactory.class);
        dfdlParser.setResourceConfig(resourceConfig);
        dfdlParser.setApplicationContext(new MockApplicationContext());
        dfdlParser.setContentHandler(saxContentHandler);

        MockExecutionContext mockExecutionContext = new MockExecutionContext();
        mockExecutionContext.setContentEncoding(StandardCharsets.ISO_8859_1.name());
        dfdlParser.setExecutionContext(mockExecutionContext);

        dfdlParser.postConstruct();
        dfdlParser.parse(new InputSource(new InputStreamReader(getClass().getResourceAsStream("/data/simpleCSV.nonUtf8Encoding.csv"), StandardCharsets.ISO_8859_1)));

        assertEquals(TextUtils.trimLines(StreamUtils.readStreamAsString(getClass().getResourceAsStream("/data/simpleCSV.nonUtf8Encoding.xml"), "UTF-8")), stringWriter.toString());
    }
}
