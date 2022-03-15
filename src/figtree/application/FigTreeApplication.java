/*
 * FigTreeApplication.java
 *
 * Copyright (C) 2006-2014 Andrew Rambaut
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

/**
 * TracerApp.java
 *
 * Title:			Tracer
 * Description:		An application for analysing MCMC trace files.
 * @author			Andrew Rambaut
 * @author			Alexei Drummond
 * @version			$Id$
 */

package figtree.application;

import com.itextpdf.text.DocumentException;
import figtree.application.preferences.AppearancePreferencesSection;
import figtree.application.preferences.FontsPreferencesSection;
import figtree.treeviewer.ExtendedTreeViewer;
import jam.controlpalettes.BasicControlPalette;
import jam.controlpalettes.ControlPalette;
import jam.framework.*;
import jam.mac.Utils;
import jebl.evolution.io.*;
import jebl.evolution.taxa.Taxon;
import jebl.evolution.trees.RootedTree;
import jebl.evolution.trees.SimpleRootedTree;
import jebl.evolution.trees.Tree;
import jebl.util.Attributable;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.text.NumberFormat;
import java.text.DecimalFormat;

/**
 * Application class for FigTree including main() method for invoking it.
 * Uses JAM Application classes to create a MultiDoc Application.
 *
 * @author Andrew Rambaut
 * @version $Id$
 *
 * $HeadURL$
 *
 * $LastChangedBy$
 * $LastChangedDate$
 * $LastChangedRevision$
 */
public class FigTreeApplication extends MultiDocApplication {

    public static final String VERSION = "1.4.4";
    public static final String DATES = "2006-2018";

    public static FigTreeApplication application;

    public FigTreeApplication(MenuBarFactory menuBarFactory, String nameString,  String titleString, String aboutString, Icon icon,
                              String websiteURLString,
                              String helpURLString) {
        super(menuBarFactory, nameString, titleString, aboutString, icon, websiteURLString, helpURLString);

//        addPreferencesSection(new GeneralPreferencesSection());
        addPreferencesSection(new AppearancePreferencesSection());
        addPreferencesSection(new FontsPreferencesSection());
//        addPreferencesSection(new AdvancedPreferencesSection());
    }

    public DocumentFrame doOpenFile(File file) {
        DocumentFrame documentFrame = getUpperDocumentFrame();
        if (documentFrame != null && documentFrame.getFile() == null) {
            documentFrame.openFile(file);
            return documentFrame;
        } else {
            return super.doOpenFile(file);
        }
    }

    public void doPaste() {

    }

    static public void createGraphic(String graphicFormat, int width, int height, String treeFileName, String graphicFileName, Map<String, Object> cmdSettings, boolean writeNewick, boolean writeNexus, Map<String, Object> colorMap) {

        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(treeFileName));
            String line = bufferedReader.readLine();
            while (line != null && line.length() == 0) {
                line = bufferedReader.readLine();
            }

            bufferedReader.close();

            boolean isNexus = (line != null && line.toUpperCase().contains("#NEXUS"));

            Reader reader = new FileReader(treeFileName);

            Map<String, Object> settings = new HashMap<String, Object>();

            ExtendedTreeViewer treeViewer = new ExtendedTreeViewer();
            ControlPalette controlPalette = new BasicControlPalette(FigTreePanel.CONTROL_PALETTE_WIDTH, BasicControlPalette.DisplayMode.ONLY_ONE_OPEN);
            FigTreePanel figTreePanel = new FigTreePanel(null, treeViewer, controlPalette);

            // First of all, fully populate the settings map if it isn't already populated so that
            // all the settings have defaults
            if (settings.size() == 0) {
                controlPalette.getSettings(settings);
            }
            // Add any command line, default ordering and font size settings
            settings.putAll(cmdSettings);

            List<Tree> trees = new ArrayList<Tree>();

            if (isNexus) {
                FigTreeNexusImporter importer = new FigTreeNexusImporter(reader);
                trees.add(importer.importNextTree());

                // Try to find a figtree block and if found, parse the settings
                while (true) {
                    try {
                        importer.findNextBlock();
                        if (importer.getNextBlockName().equalsIgnoreCase("FIGTREE")) {
                            importer.parseFigTreeBlock(settings);
                        }
                    } catch (EOFException ex) {
                        break;
                    }
                }
            } else {
                NewickImporter importer = new NewickImporter(reader, true);
                trees.add(importer.importNextTree());
            }

            if (trees.size() == 0) {
                throw new ImportException("This file contained no trees.");
            }

            // insert color choices
            int numberOfTaxa = 0;
            Set<Taxon> taxa = new HashSet<Taxon>();
            for (Tree tree : trees) {
                taxa.addAll(tree.getTaxa());
                numberOfTaxa = numberOfTaxa + taxa.size();
                for (Taxon taxon : taxa) {
                    for (String key : colorMap.keySet()) {
                        if (taxon.getName().contains(key)) {
                            taxon.setAttribute("!color", colorMap.get(key));
                        }
                    }
                }

            }
            int FONT_SIZE = 8;
            int TOP_BOTTOM_MARGIN = 46;
            int calculatedHeight = (numberOfTaxa * FONT_SIZE) + TOP_BOTTOM_MARGIN;
            ////////////
            treeViewer.setTrees(trees);

            controlPalette.setSettings(settings);

            // Jeff replaced height with calculatedHeight
            treeViewer.getContentPane().setSize(width, calculatedHeight);

            OutputStream stream;
            if (graphicFileName != null) {
                stream = new FileOutputStream(graphicFileName);
            } else {
                stream = System.out;
            }

            GraphicFormat format = null;
            if (graphicFormat.equals("PDF")) {
                format = GraphicFormat.PDF;
            } else if (graphicFormat.equals("SVG")) {
                format = GraphicFormat.SVG;
            } else if (graphicFormat.equals("GIF")) {
                format = GraphicFormat.GIF;
            } else if (graphicFormat.equals("PNG")) {
                format = GraphicFormat.PNG;
            } else if (graphicFormat.equals("JPEG")) {
                format = GraphicFormat.JPEG;
            } else {
                throw new RuntimeException("Unknown graphic format");
            }

            if (graphicFileName != null) {
                System.out.println("Creating " + graphicFormat + " graphic: " + graphicFileName);
            }

            FigTreeFrame.exportGraphics(format, treeViewer.getContentPane(), stream);

            if (writeNewick) {
                if (graphicFileName != null) {
                    String newickFileName = graphicFileName.substring(0, graphicFileName.length() - 4);
                    newickFileName = newickFileName + "_newick.tre";
                    FileWriter fileNewick = new FileWriter(newickFileName);
                    List<Tree> treesList = new ArrayList<Tree>();
                    treesList.addAll(treeViewer.getTreesAsViewed());
                    System.out.println("Creating Newick file: " + newickFileName);

                    if (treesList.size() > 0) {
                        NewickExporter newickExporter = new NewickExporter(fileNewick);
                        newickExporter.exportTrees(treesList);
                    }
                    fileNewick.flush();
                    fileNewick.close();
                } else {
                    System.out.println("Can't build Newick file name - make sure graphic file name is provided.");
                }
            }

            if (writeNexus) {
                if (graphicFileName != null) {
                    String nexusFileName = graphicFileName.substring(0, graphicFileName.length() - 4);
                    nexusFileName = nexusFileName + "_nexus.tre";
                    FileWriter fileNexus = new FileWriter(nexusFileName);
                    List<Tree> treesList = new ArrayList<Tree>();
                    treesList.addAll(treeViewer.getTreesAsViewed());
                    System.out.println("Creating Nexus file: " + nexusFileName);

                    if (treesList.size() > 0) {
                        FigTreeNexusExporter nexusExporter = new FigTreeNexusExporter(fileNexus, true);
                        Taxon[] taxonArray = taxa.toArray(new Taxon[0]);
                        int len = taxonArray.length;
                        fileNexus.write("begin taxa;\n");
                        fileNexus.write("\tdimensions ntax=" + taxonArray.length + ";\n");
                        fileNexus.write("\ttaxlabels\n");
                        for(int i = 0; i < len; ++i) {
                            Taxon taxon = taxonArray[i];
                            StringBuilder builder = new StringBuilder("\t");
                            String name = taxon.getName();
                            if (!name.matches("^(\\w|-)+$")) {
                                name = name.replace("'", "''");
                                builder.append("'").append(name).append("'");
                            } else {
                                builder.append(name);
                            }
                            appendAttributes(taxon, (String[])null, builder);
                            fileNexus.write(builder.toString() + "\n");
                        }
                        fileNexus.write(";\nend;\n");
                        nexusExporter.exportTrees(treesList);
                        nexusExporter.writeFigTreeBlock(settings);
                    }
                    fileNexus.flush();
                    fileNexus.close();
                } else {
                    System.out.println("Can't build Nexus file name - make sure graphic file name is provided.");
                }
            }

        } catch(ImportException ie) {
            throw new RuntimeException("Error writing graphic file: " + ie.getMessage());
        } catch(IOException ioe) {
            throw new RuntimeException("Error writing graphic file: " + ioe.getMessage());
        } catch (DocumentException de) {
            throw new RuntimeException("Error writing graphic file: " + de.getMessage());
        }

    }

    static private StringBuilder appendAttributes(Attributable item, String[] excludeKeys, StringBuilder builder) {
        boolean first = true;
        Iterator i$ = item.getAttributeNames().iterator();

        while (i$.hasNext()) {
            String key = (String) i$.next();
            boolean exclude = false;
            if (excludeKeys != null) {
                String[] arr$ = excludeKeys;
                int len$ = excludeKeys.length;

                for (int i = 0; i < len$; ++i) {
                    String eKey = arr$[i];
                    if (eKey.equals(key)) {
                        exclude = true;
                    }
                }
            }

            Object value = item.getAttribute(key);
            if (!exclude && !key.startsWith("&") && value != null) {
                if (first) {
                    builder.append("[&");
                    first = false;
                } else {
                    builder.append(",");
                }

                if (key.indexOf(32) < 0) {
                    builder.append(key);
                } else {
                    builder.append("\"").append(key).append("\"");
                }

                builder.append('=');
                appendAttributeValue(value, builder);
            }
        }

        if (!first) {
            builder.append("]");
        }

        return builder;
    }

    static private StringBuilder appendAttributeValue(Object value, StringBuilder builder) {
        if (!(value instanceof Object[])) {
            if (value instanceof Color) {
                return builder.append("#").append(Integer.toHexString(((Color)value).getRGB()).substring(2));
            } else {
                return value instanceof String ? builder.append("\"").append(value).append("\"") : builder.append(value);
            }
        } else {
            builder.append("{");
            Object[] elements = (Object[])((Object[])value);
            if (elements.length > 0) {
                appendAttributeValue(elements[0], builder);

                for(int i = 1; i < elements.length; ++i) {
                    builder.append(",");
                    appendAttributeValue(elements[i], builder);
                }
            }

            return builder.append("}");
        }
    }


    public static void centreLine(String line, int pageWidth) {
        int n = pageWidth - line.length();
        int n1 = n / 2;
        for (int i = 0; i < n1; i++) { System.out.print(" "); }
        System.out.println(line);
    }

    public static void printTitle() {
        System.out.println();
        centreLine("FigTree v" + VERSION + ", " + DATES, 60);
        centreLine("Tree Figure Drawing Tool", 60);
        centreLine("Andrew Rambaut", 60);
        System.out.println();
        centreLine("Institute of Evolutionary Biology", 60);
        centreLine("University of Edinburgh", 60);
        centreLine("a.rambaut@ed.ac.uk", 60);
        System.out.println();
        centreLine("http://tree.bio.ed.ac.uk/", 60);
        centreLine("Uses the Java Evolutionary Biology 2 Library (JEBL2)", 60);
        centreLine("http://jebl2.googlecode.com/", 60);
        centreLine("Uses the iText PDF Library", 60);
        centreLine("http://itextpdf.com/", 60);
        centreLine("Uses the Apache Batik Library", 60);
        centreLine("http://xmlgraphics.apache.org/batik/", 60);
        centreLine("Uses the JDOM XML Library", 60);
        centreLine("http://www.jdom.org/", 60);
        centreLine("Thanks to Alexei Drummond, Joseph Heled, Philippe Lemey, ", 60);
        centreLine("Tulio de Oliveira, Oliver Pybus, Beth Shapiro & Marc Suchard", 60);
        System.out.println();
    }

    public static void printUsage(Arguments arguments) {

        arguments.printUsage("figtree", "[<tree-file-name>] [<graphic-file-name>]");
        System.out.println();
        System.out.println("  Example: figtree test.tree");
        System.out.println("  Example: figtree -graphic PDF test.tree test.pdf");
        System.out.println("  Example: figtree -graphic PNG -width 320 -height 320 test.tree test.png");
        System.out.println();
    }

    private static boolean lafLoaded = false;

    // Main entry point
    static public void main(String[] args) {

        // There is a major issue with languages that use the comma as a decimal separator.
        // To ensure compatibility between programs in the package, enforce the US locale.
        //Locale.setDefault(Locale.US);

        Arguments arguments = new Arguments(
                new Arguments.Option[] {
                        new Arguments.StringOption("graphic", new String[] {
                                "PDF",
                                "SVG",
                                // "SWF", "PS", "EMF",
                                "PNG",
                                // "GIF",
                                "JPEG"
                        }, false, "produce a graphic with the given format"),
                        new Arguments.IntegerOption("width", "the width of the graphic in pixels"),
                        new Arguments.IntegerOption("height", "the height of the graphic in pixels"),
                        new Arguments.Option("url", "the input file is a URL"),
                        new Arguments.Option("help", "option to print this message"),
                        new Arguments.Option("newickexport", "export the displayed tree in Newick format"),
                        new Arguments.Option("nexusexport", "export the displayed tree in Nexus format"),
                        new Arguments.Option("stdout", "write the image file to stdout"),
                        new Arguments.StringOption("colors", "text:color", "comma delimited list of colors to associate with a text pattern (e.g. V704_0026_232:#3333ff) OR use the keyword 'extract' to extract from file name (expected format is hyphen delimited)"),
                        new Arguments.IntegerOption("avg_seq_length", "average length of sequences")
                });

        try {
            arguments.parseArguments(args);
        } catch (Arguments.ArgumentException ae) {
            System.out.println();
            System.out.println(ae.getMessage());
            System.out.println();
            printTitle();
            printUsage(arguments);
            System.exit(1);
        }

        if (arguments.hasOption("help")) {
            printTitle();
            printUsage(arguments);
            System.exit(0);
        }

        Map<String, Object> settingsMap = new HashMap<String, Object>();
        settingsMap.put("branchLabels.fontSize", new Double("9"));
        settingsMap.put("trees.order", true);
        settingsMap.put("trees.orderType", "increasing");
        if (arguments.hasOption("avg_seq_length")) {
            int seqLen = arguments.getIntegerOption("avg_seq_length");
            double scaleR = 1.0/(double) seqLen;
            NumberFormat formatter = new DecimalFormat("0.0E0");
            settingsMap.put("scaleBar.automaticScale", false);
            settingsMap.put("scaleBar.scaleRange", new Double(formatter.format(scaleR)));
        }

        boolean exportNewick = false;

        if (arguments.hasOption("newickexport")) {
            exportNewick = true;
        }

        boolean stdout = false;

        if (arguments.hasOption("stdout")) {
            stdout = true;
        }

        boolean exportNexus = false;

        if (arguments.hasOption("nexusexport")) {
            exportNexus = true;
        }

        Map<String, Object> colorMap = new HashMap<String, Object>();
        boolean extractColorsFromFileName = false;
        if (arguments.hasOption("colors")) {
            String colors = arguments.getStringOption("colors");
            if (colors.equals("extract")) {
                extractColorsFromFileName = true;
            } else {
                List<String> items = Arrays.asList(colors.split("\\s*,\\s*"));
                for (String item : items) {
                    if (!item.contains(":")) {
                        System.out.println("Missing : in colors value.  Should be a comma delimited list of text:color (e.g. V704_0026_232:#3333ff).");
                        System.exit(0);
                    }
                    String[] keyPair = item.split(":");
                    try {
                        colorMap.put(keyPair[0], Color.decode(keyPair[1]));
                    } catch (NumberFormatException e) {
                        System.out.println("Failed to decode color: " + keyPair[1] + ".  Should be a comma delimited list of text:color (e.g. V704_0026_232:#3333ff).");
                        System.exit(0);
                    }
                }
            }
        }

        if (arguments.hasOption("graphic")) {

            int width = 800;
            int height = 600;

            if (arguments.hasOption("width")) {
                width = arguments.getIntegerOption("width");
            }

            if (arguments.hasOption("height")) {
                height = arguments.getIntegerOption("height");
            }

            // command line version...
            String graphicFormat = arguments.getStringOption("graphic");
            String[] args2 = arguments.getLeftoverArguments();

            if (args2.length == 0) {
                // no tree file specified
                printTitle();
                printUsage(arguments);
                System.exit(0);
            } else if (args2.length == 1) {
                // no graphic file specified - write to stdout
                if (extractColorsFromFileName) {
                    parseColorMapFromTimePoints(colorMap, args2[0]);
                }
                String graphicFileName = args2[0] + ".svg";
                if (!stdout) {
                    createGraphic(graphicFormat, width, height, args2[0], graphicFileName, settingsMap, exportNewick, exportNexus, colorMap);
                } else {
                    createGraphic(graphicFormat, width, height, args2[0], null, settingsMap, exportNewick, exportNexus, colorMap);
                }
                System.exit(0);
            } else {
                printTitle();
                if (extractColorsFromFileName) {
                    parseColorMapFromTimePoints(colorMap, args2[0]);
                }
                createGraphic(graphicFormat, width, height, args2[0], (args2.length > 1 ? args2[1] : null), settingsMap, exportNewick, exportNexus, colorMap);
                System.exit(0);
            }
        }

        if (Utils.isMacOSX()) {
            if (Utils.getMacOSXMajorVersionNumber() >= 5) {
                System.setProperty("apple.awt.brushMetalLook","true");
            }

            System.setProperty("apple.laf.useScreenMenuBar","true");
            System.setProperty("apple.awt.draggableWindowBackground","true");
            System.setProperty("apple.awt.showGrowBox","true");
            System.setProperty("apple.awt.graphics.UseQuartz","true");

            try {
                // set the Quaqua Look and Feel in the UIManager
                javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
                    public void run() {
                        try {

                            // Only override the UI's necessary for ColorChooser and
                            // FileChooser:
                            Set includes = new HashSet();
                            includes.add("ColorChooser");
                            includes.add("FileChooser");
                            includes.add("Component");
                            includes.add("Browser");
                            includes.add("Tree");
                            includes.add("SplitPane");
                            includes.add("TitledBorder");

                            Class<?> qm = Class.forName("ch.randelshofer.quaqua.QuaquaManager");
                            Method method = qm.getMethod("setIncludedUIs", Set.class);
                            method.invoke(null, includes);

                            UIManager.setLookAndFeel(
                                    "ch.randelshofer.quaqua.QuaquaLookAndFeel"
                            );

                            lafLoaded = true;
                        } catch (Exception ignored) {
                        }
                    }
                });
            } catch (Exception ignored) {
            }

            UIManager.put("SystemFont", new Font("Lucida Grande", Font.PLAIN, 13));
            UIManager.put("SmallSystemFont", new Font("Lucida Grande", Font.PLAIN, 11));
        }

        if (!lafLoaded) {
            UIManager.LookAndFeelInfo[] lafs = UIManager.getInstalledLookAndFeels();
            for (UIManager.LookAndFeelInfo laf : lafs) {
                System.out.println(laf);
            }

            try {
                // set the System Look and Feel in the UIManager
                javax.swing.SwingUtilities.invokeAndWait(new Runnable() {
                    public void run() {
                        try {
                            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
//                              UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        java.net.URL url = FigTreeApplication.class.getResource("images/figtreeLogo.png");
        Icon icon = null;

        if (url != null) {
            icon = new ImageIcon(url);
        }

        final String nameString = "FigTree";
        String titleString = "<html>" +
                "<div style=\"font-family:'Helvetica Neue', Helvetica, Arial, 'Lucida Grande',sans-serif\">" +
                "<p style=\"font-weight: 100; font-size: 36px\">FigTree</p>" +
                "<p style=\"font-weight: 200; font-size: 14px\">Tree Figure Drawing Tool</p>" +
                "<p style=\"font-weight: 300; font-size: 12px\">Version " + VERSION + "</p>" +
                "</div></html>";
        String aboutString = "<html>" +
                "<div style=\"font-family:'Helvetica Neue', Helvetica, Arial, 'Lucida Grande',sans-serif\">" +
                "<center>"+ DATES + ", Andrew Rambaut<br>" +
                "Institute of Evolutionary Biology, University of Edinburgh.<br>" +
                "<a href=\"http://tree.bio.ed.ac.uk/\">http://tree.bio.ed.ac.uk/</a><br><br>" +
                "Source code available from:<br>" +
                "<a href=\"https://github.com/rambaut/figtree\">http://github.com/rambaut/figtree/</a><br><br>" +
                "Uses the Java Evolutionary Biology 2 Library (JEBL2)<br>" +
                "<a href=\"https://github.com/rambaut/jebl2/\">http://github.com/rambaut/jebl2/</a><br><br>" +
                "Thanks to Alexei Drummond, Joseph Heled, Philippe Lemey, <br>Tulio de Oliveira, Oliver Pybus, Beth Shapiro & Marc Suchard</center>" +
                "</div></html>";

        String websiteURLString = "http://tree.bio.ed.ac.uk/software/figtree/";
        String helpURLString = "http://tree.bio.ed.ac.uk/software/figtree/";

        FigTreeApplication.application = new FigTreeApplication(new FigTreeMenuBarFactory(), nameString, titleString, aboutString, icon,
                websiteURLString, helpURLString);

        application.setDocumentFrameFactory(new DocumentFrameFactory() {
            public DocumentFrame createDocumentFrame(Application app, MenuBarFactory menuBarFactory) {
                return new FigTreeFrame(nameString + " v" + VERSION);
            }
        });

        application.initialize();

        boolean useURLs = arguments.hasOption("url");
        String[] leftoverArguments = arguments.getLeftoverArguments();
        if (leftoverArguments.length > 0) {
            for (String arg : leftoverArguments) {
                if (useURLs) {
                    FigTreeFrame frame = (FigTreeFrame)application.doNew();
                    try {
                        frame.readFromURL(new URL(arg));
                    } catch (IOException e) {
                    }
                } else {
                    application.doOpen(arg);
                }
            }
        }

//		if (!jam.mac.Utils.isMacOSX() && application.getUpperDocumentFrame() == null) {
//			// If we haven't opened any files by now, prompt for one...
//			application.doOpen();
//		}

        if (application.getUpperDocumentFrame() == null) {
            // If we haven't opened any files by now, open a blank window...
            application.doNew();
        }
    }

    private static void parseColorMapFromTimePoints(Map<String, Object> colorMap, String fileName) {
        if (fileName.contains("-")) {
            if (fileName.contains(File.separator)) {
                fileName = fileName.substring(fileName.lastIndexOf(File.separator) + 1);
            }
            //System.out.println(fileName);
            int firstHyphen = fileName.indexOf("-");
            int lastHyphen = fileName.lastIndexOf("-");

            Map<Integer, Object> colorOrder = new HashMap<Integer, Object>();
            colorOrder.put(0, Color.BLACK);
            colorOrder.put(1, Color.BLUE);
            colorOrder.put(2, Color.MAGENTA);
            colorOrder.put(3, Color.ORANGE.darker());
            colorOrder.put(4, Color.GREEN.darker());

            if (firstHyphen > 0) {
                String temp = fileName.substring(0, firstHyphen);
                int beginTimePoints = temp.lastIndexOf("_") + 1;
                temp = fileName.substring(beginTimePoints);
                int endTimePoints = temp.indexOf("_");
                temp = temp.substring(0, endTimePoints);
                //System.out.println(temp);
                String name = fileName.substring(0, beginTimePoints);
                //System.out.println(name);
                String[] timepoints = temp.split("-");
                int numPoints = timepoints.length;
                int cnt = 0;
                for( int i=0; i < timepoints.length; i++) {
                    if (!timepoints[i].toLowerCase().contains("mod")) {
                        colorMap.put(name + timepoints[i], colorOrder.get(cnt));
                        cnt++;
                    }
                }
            }
        }
    }

}