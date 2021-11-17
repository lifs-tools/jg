/*
 * Copyright 2020  nils.hoffmann.
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
package org.lifstools.jgoslin.cli;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.lang3.tuple.Pair;
import org.lifstools.jgoslin.domain.ConstraintViolationException;
import org.lifstools.jgoslin.domain.FattyAcid;
import org.lifstools.jgoslin.domain.FunctionalGroup;
import org.lifstools.jgoslin.domain.LipidAdduct;
import org.lifstools.jgoslin.domain.LipidClassMeta;
import org.lifstools.jgoslin.domain.LipidClasses;
import org.lifstools.jgoslin.domain.LipidLevel;
import org.lifstools.jgoslin.domain.LipidParsingException;
import org.lifstools.jgoslin.domain.LipidSpeciesInfo;
import org.lifstools.jgoslin.parser.BaseParserEventHandler;
import org.lifstools.jgoslin.parser.FattyAcidParser;
import org.lifstools.jgoslin.parser.GoslinParser;
import org.lifstools.jgoslin.parser.HmdbParser;
import org.lifstools.jgoslin.parser.LipidMapsParser;
import org.lifstools.jgoslin.parser.Parser;
import org.lifstools.jgoslin.parser.ShorthandParser;
import org.lifstools.jgoslin.parser.SwissLipidsParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create a new command line parser for parsing of lipid names.
 *
 * @author nils.hoffmann
 */
public class CmdLineParser {

    private static final Logger log = LoggerFactory.getLogger(CmdLineParser.class);

    public static final String LIPIDMAPS_CLASS_REGEXP = ".+\\[([A-Z0-9]+)\\]";
    private static final Map<Grammar, Parser<LipidAdduct>> DEFAULT_PARSERS = loadParsers();
    private static final LipidClasses LIPID_CLASSES = LipidClasses.getInstance();

    private static String getAppInfo() throws IOException {
        Properties p = new Properties();
        p.load(CmdLineParser.class.getResourceAsStream(
                "/application.properties"));
        StringBuilder sb = new StringBuilder();
        String buildDate = p.getProperty("app.build.date", "no build date");
        if (!"no build date".equals(buildDate)) {
            Instant instant = Instant.ofEpochMilli(Long.parseLong(buildDate));
            buildDate = instant.toString();
        }
        /*
         *Property keys are in src/main/resources/application.properties
         */
        sb.append("Running ").
                append(p.getProperty("app.name", "undefined app")).
                append("\n\r").
                append(" version: '").
                append(p.getProperty("app.version", "unknown version")).
                append("'").
                append("\n\r").
                append(" build-date: '").
                append(buildDate).
                append("'").
                append("\n\r").
                append(" scm-location: '").
                append(p.getProperty("scm.location", "no scm location")).
                append("'").
                append("\n\r").
                append(" commit: '").
                append(p.getProperty("scm.commit.id", "no commit id")).
                append("'").
                append("\n\r").
                append(" branch: '").
                append(p.getProperty("scm.branch", "no branch")).
                append("'").
                append("\n\r");
        return sb.toString();
    }

    /**
     * <p>
     * Runs the command line parser for jgoslin, including validation.</p>
     *
     * @param args an array of {@link java.lang.String} lipid names.
     * @throws java.lang.Exception if any unexpected errors occur.
     */
    @SuppressWarnings("static-access")
    public static void main(String[] args) throws Exception {
        CommandLineParser parser = new PosixParser();
        Options options = new Options();
        String helpOpt = addHelpOption(options);
        String versionOpt = addVersionOption(options);
        String lipidNameOpt = addLipidNameInputOption(options);
        String lipidFileOpt = addLipidFileInputOption(options);
        String outputToFileOpt = addOutputToFileOption(options);
        String grammarOpt = addGrammarOption(options);

        CommandLine line = parser.parse(options, args);
        if (line.getOptions().length == 0 || line.hasOption(helpOpt)) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("jgoslin-cli", options);
        } else if (line.hasOption(versionOpt)) {
            log.info(getAppInfo());
        } else {
            boolean toFile = false;
            if (line.hasOption(outputToFileOpt)) {
                toFile = true;
            }
            Stream<String> lipidNames = Stream.empty();
            if (line.hasOption(lipidNameOpt)) {
                lipidNames = Stream.of(line.getOptionValues(lipidNameOpt));
            } else if (line.hasOption(lipidFileOpt)) {
                lipidNames = Files.lines(new File(line.getOptionValue(lipidFileOpt)).toPath()).filter((t) -> {
                    return !t.isEmpty();
                });
            }
            List<Pair<String, List<ValidationResult>>> results = Collections.emptyList();
            if (line.hasOption(grammarOpt)) {
                results = parseNamesWith(lipidNames, Grammar.valueOf(line.getOptionValue(grammarOpt)));
            } else {
                results = parseNames(lipidNames);
            }
            if (results.isEmpty()) {
                log.info("No results generated. Please check input file or lipid names passed on the cli!");
                System.exit(1);
            }
            if (toFile) {
                log.info("Saving output to 'goslin-out.tsv'.");
                boolean successful = writeToFile(new File("goslin-out.tsv"), results);
                if (!successful) {
                    System.exit(1);
                }
            } else {
                log.info("Echoing output to stdout.");
                boolean successful = writeToStdOut(results);
                if (!successful) {
                    System.exit(1);
                }
            }
        }
    }

    private static enum Grammar {
        GOSLIN, GOSLIN_FRAGMENTS, LIPIDMAPS, SWISSLIPIDS, HMDB, SHORTHAND2020, FATTY_ACID, NONE
    };

    record ValidationResult(
            String lipidName,
            Grammar grammar,
            LipidLevel level,
            List<String> messages,
            LipidAdduct lipidAdduct,
            LipidSpeciesInfo lipidSpeciesInfo,
            String canonicalName,
            String lipidMapsCategory,
            String lipidMapsClass,
            List<FattyAcid> fattyAcids) {

        public ValidationResult          {
            Objects.requireNonNull(messages);
            Objects.requireNonNull(fattyAcids);
        }
    }

    private static boolean writeToStdOut(List<Pair<String, List<ValidationResult>>> results) {

        try (StringWriter sw = new StringWriter()) {
            try (BufferedWriter bw = new BufferedWriter(sw)) {
                writeToWriter(bw, results);
            }
            sw.flush();
            sw.close();
            log.info(sw.toString());
            return true;
        } catch (IOException ex) {
            log.error("Caught exception while trying to write validation results string!", ex);
            return false;
        }
    }

    private static boolean writeToFile(File f, List<Pair<String, List<ValidationResult>>> results) {

        try (BufferedWriter bw = Files.newBufferedWriter(f.toPath())) {
            writeToWriter(bw, results);
            return true;
        } catch (IOException ex) {
            log.error("Caught exception while trying to write validation results to file " + f, ex);
            return false;
        }
    }

    private static String toTable(List<Pair<String, List<ValidationResult>>> results) {
        StringBuilder sb = new StringBuilder();
        HashSet<String> keys = new LinkedHashSet<>();
        List<ValidationResult> validationResults = results.stream().map((t) -> {
            return t.getValue();
        }).flatMap(List::stream).collect(Collectors.toList());
        List<Map<String, String>> entries = validationResults.stream().map((t) -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("Normalized Name", Optional.ofNullable(t.canonicalName).orElse(""));
            m.put("Original Name", t.lipidName);
            m.put("Grammar", t.grammar.name());
            m.put("Message", t.messages.stream().collect(Collectors.joining(" | ")));
            if (t.lipidAdduct != null) {
                m.put("Adduct", t.lipidAdduct.getAdduct() == null ? "" : t.lipidAdduct.getAdduct().getLipidString());
                m.put("Sum Formula", t.lipidAdduct.getSumFormula());
                m.put("Mass", String.format(Locale.US, "%.4f", t.lipidAdduct.getMass()));
                m.put("Lipid Maps Category", t.lipidAdduct.getLipid().getHeadGroup().getLipidCategory().getFullName() + " [" + t.lipidAdduct.getLipid().getHeadGroup().getLipidCategory().name() + "]");
                LipidClassMeta lclass = LIPID_CLASSES.get(t.lipidAdduct.getLipid().getInfo().lipidClass);
                m.put("Lipid Maps Main Class", lclass.description);
                String lclassAbbr = getLipidMapsClassAbbreviation(lclass.description);
                m.put("Functional Class Abbr", "[" + lclassAbbr + "]");
                m.put("Functional Class Synonyms", "[" + lclass.synonyms.stream().collect(Collectors.joining(", ")) + "]");
                m.put("Level", t.level.name());
                m.put("Total #C", t.lipidSpeciesInfo.getNumCarbon() + "");
                m.put("Total #DB", t.lipidSpeciesInfo.getDoubleBonds().getNumDoubleBonds() + "");
                for (String functionalGroupKey : t.lipidSpeciesInfo.getFunctionalGroups().keySet()) {
                    ArrayList<FunctionalGroup> fg = t.lipidSpeciesInfo.getFunctionalGroups().get(functionalGroupKey);
                    String fgCounts = fg.stream().map((sfg) -> {
                        return "" + sfg.getCount();
                    }).collect(Collectors.joining("|"));
                    m.put("Total #" + functionalGroupKey, fgCounts);
                }
                for (FattyAcid fa : t.fattyAcids()) {
                    m.put(fa.getName() + " SN Position", fa.getPosition() + "");
                    m.put(fa.getName() + " #C", fa.getNumCarbon() + "");
                    m.put(fa.getName() + " #DB", fa.getDoubleBonds().getNumDoubleBonds() + "");
                    m.put(fa.getName() + " Bond Type", fa.getLipidFaBondType().name() + "");
                    String dbPositions = fa.getDoubleBonds().getDoubleBondPositions().entrySet().stream().map((entry) -> {
                        return entry.getKey() + "" + entry.getValue();
                    }).collect(Collectors.joining("|"));
                    m.put(fa.getName() + " DB Positions", dbPositions + "");
                    for (String functionalGroupKey : fa.getFunctionalGroups().keySet()) {
                        ArrayList<FunctionalGroup> fg = fa.getFunctionalGroups().get(functionalGroupKey);
                        String fgCounts = fg.stream().map((sfg) -> {
                            return "" + sfg.getCount();
                        }).collect(Collectors.joining("|"));
                        m.put("Total #" + functionalGroupKey, fgCounts);
                    }
                }
            } else {
                m.put("Lipid Maps Category", "");
                m.put("Lipid Maps Main Class", "");
                m.put("Functional Class Abbr", "");
                m.put("Functional Class Synonyms", "");
                m.put("Level", "");
                m.put("Total #C", "");
                m.put("Total #DB", "");
            }
            keys.addAll(m.keySet());
            return m;
        }).collect(Collectors.toList());
        sb.append(keys.stream().collect(Collectors.joining("\t"))).append("\n");
        for (Map<String, String> m : entries) {
            List<String> l = new LinkedList();
            for (String key : keys) {
                l.add(m.getOrDefault(key, ""));
            }
            sb.append(l.stream().collect(Collectors.joining("\t"))).append("\n");
        }
        return sb.toString();
    }

    private static void writeToWriter(BufferedWriter bw, List<Pair<String, List<ValidationResult>>> results) {
        try {
            bw.write(toTable(results));
            bw.newLine();
        } catch (IOException ex) {
            log.error("Caught exception while trying to write validation results to buffered writer.", ex);
        }
    }

    private static List<Pair<String, List<ValidationResult>>> parseNames(Stream<String> lipidNames) {
        return lipidNames.map((t) -> {
            return parseName(t);
        }).collect(Collectors.toList());
    }

    private static List<Pair<String, List<ValidationResult>>> parseNamesWith(Stream<String> lipidNames, Grammar grammar) {
        return lipidNames.map((t) -> {
            return parseNameWith(t, grammar);
        }).collect(Collectors.toList()).stream().map((t) -> {
            return Pair.of(t.getKey(), Arrays.asList(t.getValue()));
        }).collect(Collectors.toList());
    }

    private static Pair<String, ValidationResult> parseNameWith(String lipidName, Grammar grammar) {
        Parser<LipidAdduct> parser = loadParsers().get(grammar);
        if (parser == null) {
            throw new ConstraintViolationException("Unsupported grammar: " + grammar);
        }
        ValidationResult validationResult;
        try {
            BaseParserEventHandler<LipidAdduct> handler = parser.newEventHandler();
            LipidAdduct la = parser.parse(lipidName, handler, false);
            if (la == null) {
                validationResult = new ValidationResult(
                        lipidName,
                        grammar,
                        LipidLevel.NO_LEVEL,
                        Arrays.asList(handler.getErrorMessage()),
                        null,
                        null,
                        null,
                        null,
                        null,
                        Collections.emptyList()
                );
                log.debug("Could not parse " + lipidName + " with " + grammar + " grammar: " + grammar + ". Message: " + handler.getErrorMessage());
                return Pair.of(lipidName, validationResult);
            } else {
                String canonicalName;
                try {
                    canonicalName = la.getLipidString();
                } catch (RuntimeException re) {
                    canonicalName = null;
                    log.debug("Parsing error for {}!", lipidName);
                }
                List<FattyAcid> fas = extractFas(la);
                validationResult = new ValidationResult(
                        lipidName,
                        grammar,
                        la.getLipidLevel(),
                        Arrays.asList(handler.getErrorMessage()),
                        la,
                        la.getLipid().getInfo(),
                        canonicalName,
                        LIPID_CLASSES.get(la.getLipid().getInfo().lipidClass).lipidCategory.name(),
                        getLipidMapsClassAbbreviation(LIPID_CLASSES.get(la.getLipid().getInfo().lipidClass).className),
                        fas
                );
                return Pair.of(lipidName, validationResult);
            }
        } catch (LipidParsingException ex) {
            validationResult = new ValidationResult(
                    lipidName,
                    grammar,
                    LipidLevel.NO_LEVEL,
                    Arrays.asList("Parsing failed for lipid '" + lipidName + "' using grammar '" + grammar + "' with exception: " + ex.getLocalizedMessage()),
                    null,
                    null,
                    null,
                    null,
                    null,
                    Collections.emptyList()
            );
            log.error("Parsing failed for lipid '" + lipidName + "' using grammar '" + grammar + "' with exception: " + ex.getLocalizedMessage(), ex);
            return Pair.of(lipidName, validationResult);
        }
    }

    private static Pair<String, List<ValidationResult>> parseName(String lipidName) {
        List<ValidationResult> results = new ArrayList<>();
        Pair<String, ValidationResult> shorthandResult = parseNameWith(lipidName, Grammar.SHORTHAND2020);
        if (shorthandResult.getValue().canonicalName != null) {
            return Pair.of(shorthandResult.getKey(), Arrays.asList(shorthandResult.getValue()));
        }
        Pair<String, ValidationResult> fattyAcid = parseNameWith(lipidName, Grammar.FATTY_ACID);
        if (fattyAcid.getValue().canonicalName != null) {
            return Pair.of(fattyAcid.getKey(), Arrays.asList(fattyAcid.getValue()));
        }
        Pair<String, ValidationResult> goslinResult = parseNameWith(lipidName, Grammar.GOSLIN);
        if (goslinResult.getValue().canonicalName != null) {
            return Pair.of(goslinResult.getKey(), Arrays.asList(goslinResult.getValue()));
        }
        Pair<String, ValidationResult> lipidMapsResult = parseNameWith(lipidName, Grammar.LIPIDMAPS);
        if (lipidMapsResult.getValue().canonicalName != null) {
            return Pair.of(lipidMapsResult.getKey(), Arrays.asList(lipidMapsResult.getValue()));
        }
        Pair<String, ValidationResult> swissLipidsResult = parseNameWith(lipidName, Grammar.SWISSLIPIDS);
        if (swissLipidsResult.getValue().canonicalName != null) {
            return Pair.of(swissLipidsResult.getKey(), Arrays.asList(swissLipidsResult.getValue()));
        }
        Pair<String, ValidationResult> hmdbResult = parseNameWith(lipidName, Grammar.HMDB);
        if (hmdbResult.getValue().canonicalName != null) {
            return Pair.of(hmdbResult.getKey(), Arrays.asList(hmdbResult.getValue()));
        }
        List<String> messages = new ArrayList<>(hmdbResult.getValue().messages);
        messages.add("Lipid name could not be parsed with any grammar!");
        ValidationResult r = new ValidationResult(lipidName, Grammar.NONE, LipidLevel.NO_LEVEL, messages, null, null, "", null, null, Collections.emptyList());
        results.add(r);
        return Pair.of(lipidName, results);
    }

    private static ArrayList<FattyAcid> extractFas(LipidAdduct la) {
        return la.getLipid().getFaList();
    }

    private static String getLipidMapsClassAbbreviation(String lipidMapsClass) {
        Pattern lmcRegexp = Pattern.compile(LIPIDMAPS_CLASS_REGEXP);
        Matcher lmcMatcher = lmcRegexp.matcher(lipidMapsClass);
        if (lmcMatcher.matches() && lmcMatcher.groupCount() == 1) {
            lipidMapsClass = lmcMatcher.group(1);
        } else {
            lipidMapsClass = null;
        }
        return lipidMapsClass;
    }

    protected static String addLipidFileInputOption(Options options) {
        String versionOpt = "file";
        options.addOption("f", versionOpt, true, "Input a file name to read from for lipid name for parsing. Each lipid name must be on a separate line.");
        return versionOpt;
    }

    protected static String addLipidNameInputOption(Options options) {
        String versionOpt = "name";
        options.addOption("n", versionOpt, true, "Input a lipid name for parsing.");
        return versionOpt;
    }

    protected static String addVersionOption(Options options) {
        String versionOpt = "version";
        options.addOption("v", versionOpt, false, "Print version information.");
        return versionOpt;
    }

    protected static String addHelpOption(Options options) {
        String helpOpt = "help";
        options.addOption("h", helpOpt, false, "Print help message.");
        return helpOpt;
    }

    protected static String addOutputToFileOption(Options options) {
        String outputToFileOpt = "outputFile";
        options.addOption("o", outputToFileOpt, false, "Write output to file 'goslin-out.tsv' instead of to std out.");
        return outputToFileOpt;
    }

    protected static String addGrammarOption(Options options) {
        String grammarOpt = "grammar";
        options.addOption("g", grammarOpt, true, "Use the provided grammar explicitly instead of all grammars. Options are: " + Arrays.toString(Grammar.values()));
        return grammarOpt;
    }

    protected static Map<Grammar, Parser<LipidAdduct>> loadParsers() {
        Map<Grammar, Parser<LipidAdduct>> parsers = new LinkedHashMap<>();
        for (Grammar grammar : Grammar.values()) {
            switch (grammar) {
                case FATTY_ACID ->
                    parsers.put(grammar, FattyAcidParser.newInstance());
                case GOSLIN ->
                    parsers.put(grammar, GoslinParser.newInstance());
                case HMDB ->
                    parsers.put(grammar, HmdbParser.newInstance());
                case LIPIDMAPS ->
                    parsers.put(grammar, LipidMapsParser.newInstance());
                case SHORTHAND2020 ->
                    parsers.put(grammar, ShorthandParser.newInstance());
                case SWISSLIPIDS ->
                    parsers.put(grammar, SwissLipidsParser.newInstance());
                case GOSLIN_FRAGMENTS -> {
                }
                case NONE -> {
                }
            }
            //FIXME skipping for now
        }
        return parsers;
    }

}
