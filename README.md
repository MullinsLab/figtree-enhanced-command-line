This source is an extension of Adam Rambaut's FigTree.  The original source can be found here:
https://github.com/rambaut/figtree/releases


Command line FigTree summary

Build Requirements:
java 8

A current version of figtree.jar can be found in dist/figtree.jar

Example execution:

Start the FigTree application and display the FigTree application frame to use in typical manual mode
java -jar figtree.jar

To use as part of a pipeline (or run command line) you will need to create a settings file:

1. Open the FigTree application using "java -jar figtree.jar".  Open a DIVEIN output tree without annotation or ordering.  Set ordering, scale bar/scale range and other settings (coloring time points will not be saved at this point).  File > Export Trees... select "Nexus" and check boxes "Save as currently displayed", "Save all trees", "Include FigTree block".

2. Open the .tre file that you just created and copy out the FigTree block:  Copy everything between and including "begin figtree;  ...  end;".  Save that to a file called YOUR_SETTING_NAME.settings.  There is an example settings file called GP.settings in the dist directory that you can look at to see the expected format.  This settings file will be used for all .tre files in batch.

Easiest way to execute multiple files in a directory with a single settings file (For this example a folder named "1_TEST4_GP_DIVEIN_results"):

1. For easiest, default execution, all .tre files expected to be run should be named something like: TEST4_2315_160-161-170_GP_544353459373.sequence.txt_phyml_tree.tre
	- the important part of the file naming is that time points are parseable from the file name programmatically using the hyphens. In this case the software will try to pattern match and color based on the names containing:
		TEST4_2315_160
		TEST4_2315_161
		TEST4_2315_170
		
		note: hyphens not delineating time points should be avoided in the file names 
		
	- coloring will occur automatically for each time point found

Single file execution:

java -jar figtree.jar -settings YOUR_SETTING_NAME.settings -colors extract -newickexport -nexusexport -graphic SVG -height 768 -width 783 PATH_TO_FILE

Batch execution of files in a folder using the same settings file:

find ./RESULTS_FOLDER_NAME/ -name '*phyml_tree.tre' -type f | awk '{print "java -jar figtree.jar -settings YOUR_SETTING_NAME.settings -colors extract -newickexport -nexusexport -graphic SVG -height 768 -width 783 "$0}' |sh

^ This command finds all files that include '*phyml_tree.tre' in their name, then prepends "java -jar figtree.jar -settings GP.settings -colors extract -newickexport -nexusexport -graphic SVG -height 768 -width 783 " to the path to each file that is listed.  The "|sh" on the end of the command causes the execution of each line.  If you run the command without the "|sh" you can see the lines that are built without executing them.



Command line parameters:

java -jar figtree.jar 
	-settings SETTINGS_FILE (e.g.  GP.settings)
	
	-colors extract (Using the keyword "extract" will attempt to find any values seperated by a "-" and append them to the prefix of the file name. Alternatively a comma delimited list of KEY:VALUE pairs (KEY is text to match with a hex color VALUE - e.g. 	V704_0026_232:#3333ff,V704_0026_231:#ff3300)
	
	-newickexport (flag to include an exported Newick formatted tree with the same name as graphic file)
	
	-nexusexport (flag to include an exported Nexus formatted tree with the same name as graphic file)
	
	-graphic FORMAT as either (SVG, PDF, PNG, JPEG) -height HEIGHT -width WIDTH INPUT_TREE_FILE OUTPUT_GRAPHIC_FILE (OUTPUT_GRAPHIC_FILE is optional)


Alternative example execution with manual color pattern match settings and output file naming:

Using the example above, TEST4_2315_160-161-170_GP_544353459373.sequence.txt_phyml_tree.tre, we can set the colors for the time points in a string for the -colors option:

e.g. -colors TEST4_2315_160:#3333ff,TEST4_2315_161:#ff33ff,TEST4_2315_170:##66ff66

To execute:

java -jar figtree.jar -settings YOUR_SETTING_NAME.settings -colors TEST4_2315_160:#3333ff,TEST4_2315_161:#ff33ff,TEST4_2315_170:##66ff66 -newickexport -nexusexport -graphic SVG -height 768 -width 783 TEST4_2315_160-161-170_GP_544353459373.sequence.txt_phyml_tree.tre TEST4_2315_160-161-170_GP_544353459373.sequence.txt_phyml_tree.svg


