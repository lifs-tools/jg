/*
 * Copyright 2021 Dominik Kopczynski, Nils Hoffmann.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lifstools.jgoslin.parser;

import org.lifstools.jgoslin.domain.KnownFunctionalGroups;
import org.lifstools.jgoslin.domain.StringFunctions;
import org.lifstools.jgoslin.domain.LipidAdduct;

/**
 *
 * @author Dominik Kopczynski
 * @author Nils Hoffmann
 */
public class ShorthandParser extends Parser<LipidAdduct> {

    private static final String DEFAULT_GRAMMAR_CONTENT = "Shorthand2020.g4";

    private final KnownFunctionalGroups knownFunctionalGroups;
    
    private ShorthandParser(KnownFunctionalGroups knownFunctionalGroups, String grammarContent, char quote) {
        super(grammarContent, quote);
        this.knownFunctionalGroups = knownFunctionalGroups;
    }

    public static ShorthandParser newInstance(KnownFunctionalGroups knownFunctionalGroups, String grammarResourcePath, char quote) {
        return new ShorthandParser(knownFunctionalGroups, StringFunctions.getResourceAsString(grammarResourcePath), quote);
    }
    
    public static ShorthandParser newInstance(KnownFunctionalGroups knownFunctionalGroups) {
        return newInstance(knownFunctionalGroups, DEFAULT_GRAMMAR_CONTENT, DEFAULT_QUOTE);
    }

    public static ShorthandParser newInstance() {
        return newInstance(new KnownFunctionalGroups(), DEFAULT_GRAMMAR_CONTENT, StringFunctions.DEFAULT_QUOTE);
    }

    @Override
    public ShorthandParserEventHandler newEventHandler() {
        return new ShorthandParserEventHandler(knownFunctionalGroups);
    }

}
