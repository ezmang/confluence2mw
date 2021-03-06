package com.convert;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.*;


import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.*;
import org.apache.commons.cli.*;

/**
 * Confluence2Mediawiki Converter - 
 * Reads a loki URL from the convert.properties file located in the directory.
 * Converts and downloads all confluence files listed at that URL.
 * Downloads all attatchments found in the resource folder.
 * @author Emilio Zand
 */
public class ConfluenceConvert {
	
	//	Constants
	 private static final String SPACE_SYMBOL = "%20";
	 private static final String REMOTE_RESOURCE_PATH = "src/site/resources/";
	 private static final String REMOTE_CONFLUENCE_PATH = "src/site/confluence/";
	 private static final String LOCAL_IMAGE_PATH = "resources/images/";
	 private static final String LOCAL_RESOURCE_PATH = "resources/";
	 
	
	/**
	 * Initiating Method
	 * @param myProps
	 * @return Variables - a hashmap containing variables needed for the conversion
	 * @throws IOException
	 */
	private static Map<String,Object> init(Properties myProps) throws IOException{
		
		/*Checks to see if convert.properties is valid*/
		if(myProps.getProperty("version")==null){
			throw new IllegalArgumentException("Error: invalid convert.properties file, no version specified");			
		}
		if(myProps.getProperty("source")==null){
			throw new IllegalArgumentException("Error: invalid convert.properties file, no source specified");			
		}
		if(myProps.getProperty("baseURL")==null){
			throw new IllegalArgumentException("Error: invalid convert.properties file, no baseURL specified");			
		}
		if(myProps.getProperty("outputDirectory")==null){
			throw new IllegalArgumentException("Error: invalid convert.properties file, no outputDirectory specified");			
		}
		
		
		Map<String,Object> Variables = new HashMap<String,Object>();
		
		/* Check and fix Output directory and Input URL syntax */
		if (myProps.getProperty("outputDirectory").endsWith("/")||myProps.getProperty("outputDirectory").endsWith("\\")){
			Variables.put("outDir", myProps.getProperty("outputDirectory"));
		}
		else{
			Variables.put("outDir", myProps.getProperty("outputDirectory") + "/");
		}
		
		if((String) myProps.get("file")!=null){
			Variables.put("file", myProps.get("file"));
		}
		if((String) myProps.get("URL")!=null){
			Variables.put("URL", myProps.get("URL"));
		}
		
		
		String inputSite = myProps.getProperty("baseURL")+myProps.getProperty("version")+"/"+myProps.getProperty("source")+"/";
		// The 12 is the length of "source/xref"
		int pathStart = inputSite.indexOf("source/xref/")+12;
		Variables.put("conFiles", getFileList(inputSite+REMOTE_CONFLUENCE_PATH));
		Variables.put("folderPath", inputSite.substring(pathStart));
		Variables.put("resourceURL", inputSite+REMOTE_RESOURCE_PATH);
		Variables.put("rawURL", inputSite.replace("xref","raw")+"src/site/");
		
		return Variables;		
	}
	
	/**
	 * Conversion and Downloading of confluence files and resources
	 * @param Variables
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	private static void convertFiles (Map<String,Object> Variables) throws IOException{
		if((String) Variables.get("file")!=null||(String) Variables.get("URL")!=null){
			if((String) Variables.get("file")!=null){
				convertLocalFile(Variables);
			}
			if((String) Variables.get("URL")!=null){
				convertURL(Variables);
			}
		}
		else{
		List<String> Resources = getFileList((String) Variables.get("resourceURL"));
		List<String> images = getFileList((String) Variables.get("resourceURL")+"images/");
		List<String> conFiles = (List<String>) Variables.get("conFiles");
		String rawURL = (String) Variables.get("rawURL");
		String outDir = (String) Variables.get("outDir");
		String folderPath = (String) Variables.get("folderPath");
		String confluence = "";
		
		for (int i = 0; i < conFiles.size();i++){
		    System.out.println(" Converting: " + conFiles.get(i));
		    File file_out = new File(outDir+folderPath+conFiles.get(i).replace(".confluence", ".mw"));
		    confluence = getConfluence(rawURL+"confluence/"+conFiles.get(i));
		    FileUtils.writeStringToFile(file_out, convert(confluence));
		}
		downloadResources(rawURL+LOCAL_RESOURCE_PATH,Resources,outDir+folderPath+LOCAL_RESOURCE_PATH);
		downloadResources(rawURL+LOCAL_IMAGE_PATH,images,outDir+folderPath+LOCAL_IMAGE_PATH);
		}
	}
	/**
	 * Converts a local .confluence file 
	 * @param Variables
	 * @throws IOException
	 */
	private static void convertLocalFile (Map<String,Object> Variables) throws IOException{
		String filePath = (String) Variables.get("file");
		File conFile = new File(filePath);
		String fileName = conFile.getName();
		String outDir = (String) Variables.get("outDir");
		String confluence = FileUtils.readFileToString(conFile);
		System.out.println(" Converting: " + fileName);
		File file_out = new File(outDir+fileName.replace(".confluence", ".mw"));
		FileUtils.writeStringToFile(file_out, convert(confluence));
	}
	
	/**
	 * Converts a .confluence file at specified URL
	 * @param Variables
	 * @throws IOException
	 */
	private static void convertURL(Map<String,Object> Variables) throws IOException{
		String outDir = (String) Variables.get("outDir");
		String URL = (String) Variables.get("URL");
		String confluence = getConfluence(URL);
		String fileName = URL.substring(URL.lastIndexOf("/")+1);
		System.out.println(" Converting: " + fileName);
		File file_out = new File(outDir+fileName.replace(".confluence",".mw"));
		FileUtils.writeStringToFile(file_out, convert(confluence));
	}
	
	/**
	 * Confluence converting method
	 * @param input - the input confluence string that is to be converted
	 * @return The converted mediawiki String
	 */
    public static String convert(String input){
	if (input==null){
		throw new IllegalArgumentException("Confluence string is null");
	}
    String text = input;
	String REGEX = null;
	String REPLACE = null;
	Pattern p = null;
	Matcher m = null; 

	text = text.replaceAll("(\n*)\\{code([\\s\\S]+?)\\{code\\}", "$1{startcode$2{endcode}");
	text = text.replaceAll("(\n*)\\{noformat([\\s\\S]+?)\\{noformat\\}", "$1{startnoformat$2{endnoformat}");
	text = text.replaceAll("[\\[](?=(?:(?!\\{startcode)[\\s\\S])*?\\{endcode\\})", "~~STARTBRACKET~~");
	text = text.replaceAll("[\\]](?=(?:(?!\\{startcode)[\\s\\S])*?\\{endcode})", "~~ENDBRACKET~~");
	text = text.replaceAll("[\\\\](?=(?:(?!\\{startcode)[\\s\\S])*?\\{endcode\\})", "~~BACKSLASH~~");
	text = text.replaceAll("[\\*](?=(?:(?!\\{startcode)[\\s\\S])*?\\{endcode\\})", "~~STAR~~");

	text = text.replaceAll("[\\[](?=(?:(?!\\{startnoformat)[\\s\\S])*?\\{endnoformat\\})", "~~STARTBRACKET~~");
	text = text.replaceAll("[\\]](?=(?:(?!\\{startnoformat)[\\s\\S])*?\\{endnoformat\\})", "~~ENDBRACKET~~");
	text = text.replaceAll("[\\\\](?=(?:(?!\\{startnoformat)[\\s\\S])*?\\{endnoformat\\})", "~~BACKSLASH~~");
	text = text.replaceAll("[\\*](?=(?:(?!\\{startnoformat)[\\s\\S])*?\\{endnoformat\\})", "~~STAR~~");
	
	/* convert confluence newlines */
	text = text.replaceAll("\134\134", "<br/>");

	/* remove anchors - mediawiki handles automatically */
	text = text.replaceAll("\\{anchor.*}", "");
	
	/* remove image borders */
	text = text.replaceAll("\\|border=1","");

	String codereplace2 = "<!-- code start-->\n<h5 style=\"text-align:center\">$1</h5><hr>\n<pre style=\"margin-left:20px; font-size:1.4em; background-color:#fdfdfd\">$2</pre><!-- code end-->\n";
	String codereplace1 = "\n<!-- code start-->\n<pre style=\"margin-left:20px; font-size:1.4em; background-color:#fdfdfd\">$2</pre><!-- code end-->\n";

	text = text.replaceAll("\\{startcode\\s*:\\s*title=([^|}]*)[|][^\\}]*}([\\s\\S]+?)\\{endcode\\}", codereplace2 );   
	text = text.replaceAll("\\{startcode\\s*:\\s*title=([^}]*)\\}([\\s\\S]+?)\\{endcode\\}", codereplace2 );
	text = text.replaceAll("\\{startcode\\s*:\\s*(.*)?\\}([\\s\\S]+?)\\{endcode\\}", codereplace1 );       
	text = text.replaceAll("\\{startcode(\\s*)\\}([\\s\\S]+?)\\{endcode\\}", codereplace1 );

	String noformatreplace2 = "<h5 style=\"text-align:center\">$1</h5><hr>\n<pre style=\"margin-left:20px; border:solid; border-color:#3C78B5; border-width:1px; font-size:1.4em; background-color:#fff\">$2</pre>";
	String noformatreplace1 = "\n<pre style=\"margin-left:20px; border:solid; border-color:#3C78B5; border-width:1px; font-size:1.4em; background-color:#fff\">$2</pre>";
	text = text.replaceAll("\\{startnoformat\\s*:\\s*title=([^|}]*)[|][^\\}]*}([\\s\\S]+?)\\{endnoformat\\}", noformatreplace2 );   
	text = text.replaceAll("\\{startnoformat\\s*:\\s*title=([^\\}]*)}([\\s\\S]+?)\\{endnoformat\\}", noformatreplace2 );
	text = text.replaceAll("\\{startnoformat\\s*:\\s*(.*)?\\}([\\s\\S]+?)\\{endnoformat\\}", noformatreplace1 );       
	text = text.replaceAll("\\{startnoformat(\\s*)\\}([\\s\\S]+?)\\{endnoformat\\}", noformatreplace1 );

	String panelreplace2 = "~~TABLESTART~~ cellpadding=\"10\" width=\"100%\" style=\"margin-left:20px; border:solid; border-color:#55a; border-width:1px; text-align:left; background-color:#f0f0f0;\"\n~~ROWSTART~~\n| *$1*<hr>\n<div style=\"white-space: pre\">\n$2\n</div>\n~~TABLEEND~~\n<br/>";
	String panelreplace1 = "~~TABLESTART~~ cellpadding=\"10\" width=\"100%\" style=\"margin-left:20px; border:solid; border-color:#55a; border-width:1px; text-align:left; background-color:#f0f0f0;\"\n~~ROWSTART~~\n|\n<div style=\"white-space: pre\">\n$2\n</div>\n~~TABLEEND~~\n<br/>";

	text = text.replaceAll("\\{panel\\s*:\\s*title=([^|\\}]*)[|][^\\}]*}([\\s\\S]+?)\\{panel\\}", panelreplace2 );   
	text = text.replaceAll("\\{panel\\s*:\\s*title=([^\\}]*)\\\\}([\\s\\S]+?)\\{panel\\}", panelreplace2 );
	text = text.replaceAll("\\{panel\\s*:\\s*(.*)?\\}([\\s\\S]+?)\\{panel\\}", panelreplace1 );       
	text = text.replaceAll("\\{panel(\\s*)\\}([\\s\\S]+?)\\{panel\\}", panelreplace1 );

	String tipreplace2 = "~~TABLESTART~~ width=\"100%\" style=\"padding: 20px; margin-left:20px; border:solid; border-color:#aaa; border-width:0px; text-align:left; background-color:#ddffdd;\"\n~~ROWSTART~~\n| <span style=\"color:#00AA00\">*TIP*:</span> *$1*<hr>\n~~ROWSTART~~\n|\n<div style=\"white-space: pre\">\n$2\n</div>\n~~TABLEEND~~\n<br/>";
	String tipreplace1 = "~~TABLESTART~~ width=\"100%\" style=\"padding: 20px; margin-left:20px; border:solid; border-color:#aaa; border-width:0px; text-align:left; background-color:#ddffdd;\"\n~~ROWSTART~~\n| <span style=\"color:#00AA00\">*TIP*</span><hr>\n~~ROWSTART~~\n|\n<div style=\"white-space: pre\">\n$2\n</div>\n~~TABLEEND~~\n<br/>";

	text = text.replaceAll("\\{tip\\s*:\\s*title=([^|\\}]*)[|][^\\}]*}([\\s\\S]+?)\\{tip\\}", tipreplace2 );   
	text = text.replaceAll("\\{tip\\s*:\\s*title=([^\\}]*)\\}([\\s\\S]+?)\\{tip\\}", tipreplace2 );
	text = text.replaceAll("\\{tip\\s*:\\s*(.*)?\\\\}([\\s\\S]+?)\\{tip\\}", tipreplace1 );       
	text = text.replaceAll("\\{tip(\\s*)\\}([\\s\\S]+?)\\{tip\\\\}", tipreplace1 );

	String inforeplace2 = "~~TABLESTART~~ width=\"100%\" style=\"padding: 20px; margin-left:20px; border:solid; border-color:#aaa; border-width:0px; text-align:left; background-color:#D8E4F1;\"\n~~ROWSTART~~\n| <span style=\"color:#0000AA\">*INFO*:</span> *$1*<hr>\n~~ROWSTART~~\n|\n<div style=\"white-space: pre\">$2</div>\n~~TABLEEND~~\n<br/>";
	String inforeplace1 = "~~TABLESTART~~ width=\"100%\" style=\"padding: 20px; margin-left:20px; border:solid; border-color:#aaa; border-width:0px; text-align:left; background-color:#D8E4F1;\"\n~~ROWSTART~~\n| <span style=\"color:#0000AA\">*INFO*</span><hr>\n~~ROWSTART~~\n|\n<div style=\"white-space: pre\">\n$2\n</div>\n~~TABLEEND~~\n<br/>";

	text = text.replaceAll("\\{info\\s*:\\s*title=([^|\\}]*)[|][^\\}]*\\\\}([\\s\\S]+?)\\{info\\}", inforeplace2 );   
	text = text.replaceAll("\\{info\\s*:\\s*title=([^\\}]*)\\}([\\s\\S]+?)\\{info\\}", inforeplace2 );
	text = text.replaceAll("\\{info\\s*:\\s*(.*)?\\}([\\s\\S]+?)\\{info\\}", inforeplace1 );       
	text = text.replaceAll("\\{info(\\s*)\\}([\\s\\S]+?)\\{info\\}", inforeplace1 );

	String notereplace2 = "~~TABLESTART~~ width=\"100%\" style=\"padding: 20px; margin-left:20px; border:solid; border-color:#aaa; border-width:0px; text-align:left; background-color:#FFFFCE;\"\n~~ROWSTART~~\n| <span style=\"color:#AAAA00\">*NOTE*:</span> *$1*<hr>\n~~ROWSTART~~\n|\n<div style=\"white-space: pre\">$2</div>\n~~TABLEEND~~\n<br/>";
	String notereplace1 = "~~TABLESTART~~   width=\"100%\" style=\"padding: 20px; margin-left:20px; border:solid; border-color:#aaa; border-width:0px; text-align:left; background-color:#FFFFCE;\"\n~~ROWSTART~~\n| <span style=\"color:#AAAA00\">*NOTE*</span><hr>\n~~ROWSTART~~\n|\n<div style=\"white-space: pre\">\n$2\n</div>\n~~TABLEEND~~\n<br/>";

	text = text.replaceAll("\\{note\\s*:\\s*title=([^|\\}]*)[|][^\\}]*\\}([\\s\\S]+?)\\{note\\}", notereplace2 );   
	text = text.replaceAll("\\{note\\s*:\\s*title=([^\\}]*)\\}([\\s\\S]+?)\\{note\\}", notereplace2 );
	text = text.replaceAll("\\{note\\s*:\\s*(.*)?\\}([\\s\\S]+?)\\{note\\}", notereplace1 );       
	text = text.replaceAll("\\{note(\\s*)\\}([\\s\\S]+?)\\{note\\}", notereplace1 );

	String warningreplace2 = "<!-- warning start -->\n~~TABLESTART~~ width=\"100%\" style=\"padding: 20px; margin-left:20px; border:solid; border-color:#aaa; border-width:0px; text-align:left; background-color:#FFCCCC;\"\n~~ROWSTART~~\n| <span style=\"color:#AA0000\">*WARNING*:</span> *$1*<hr>\n~~ROWSTART~~\n|\n<div style=\"white-space: pre\">\n$2\n</div>\n~~TABLEEND~~\n<br/>";
	String warningreplace1 = "<!-- warning start -->\n~~TABLESTART~~ width=\"100%\" style=\"padding: 20px; margin-left:20px; border:solid; border-color:#aaa; border-width:0px; text-align:left; background-color:#FFCCCC;\"\n~~ROWSTART~~\n| <span style=\"color:#AA0000\">*WARNING*</span><hr>\n~~ROWSTART~~\n|\n<div style=\"white-space: pre\">\n$2\n</div>\n~~TABLEEND~~\n<br/>";

	text = text.replaceAll("\\{warning\\s*:\\s*title=([^|\\}]*)[|][^\\}]*\\}([\\s\\S]+?)\\{warning\\}", warningreplace2 );   
	text = text.replaceAll("\\{warning\\s*:\\s*title=([^\\}]*)\\}([\\s\\S]+?)\\{warning\\}", warningreplace2 );
	text = text.replaceAll("\\{warning\\s*:\\s*(.*)?\\}([\\s\\S]+?)\\{warning\\}", warningreplace1 );       
	text = text.replaceAll("\\{warning(\\s*)\\}([\\s\\S]+?)\\{warning\\}", warningreplace1 );

	// section   
	String sectionreplace = "<!-- section start -->\n~~TABLESTART~~ border=0 width=\"100%\" cellpadding=10 align=top\n~~ROWSTART~~$1\n~~TABLEEND~~<!-- section end-->\n";
	text = text.replaceAll("(\\{section\\}([\\s\\S]+?)(\\{column\\}([\\s\\S]+?)\\{column\\})*?(.|\n)*?\\{section\\})", sectionreplace);
	text = text.replaceAll("\\{section\\}([\\s\\S]+?)\\{section\\}", "$1");


	// column
	String columnreplace1 = "\n<!-- column start -->\n~~CELLSTART~~$2\n<!-- column end -->\n";
	String columnreplace2 = "\n<!-- column start -->\n~~CELLSTART~~$2\n<!-- column end -->\n";
	text = text.replaceAll("\\{column\\s*:\\s*title=([^|\\}]*)[|][^\\}]*\\}([\\s\\S]+?)\\{column\\}", columnreplace2 );   
	text = text.replaceAll("\\{column\\s*:\\s*title=([^\\}]*)\\}([\\s\\S]+?)\\{column\\}", columnreplace2 );
	text = text.replaceAll("\\{column\\s*:\\s*(.*)?\\\\}([\\s\\S]+?)\\{column\\}", columnreplace1 );       
	text = text.replaceAll("\\{column(\\s*)\\}([\\s\\S]+?)\\{column\\}", columnreplace1 );

	/* Add newline to EOF to fix issues */
	text = text + "\n";

	/* clean up confluence garbage chars - \\ */
	text = text.replaceAll("\\\\\\s*\\\\", "<br/>");
	text = text.replaceAll("\\\\\\\\", "");
	text = text.replaceAll("\\\\\\\\", "");

	/* Replace escaped brackets - \[ */
	text = text.replaceAll("\\\\\\[", "<nowiki>[</nowiki>");

	/* Replace escape sequences - \ */
	text = text.replaceAll("\\\\(\\S)", "<nowiki>$1</nowiki>");
	
	/* detect embeded images*/
	REGEX = "^!(.+)!$";
	REPLACE = "~~ATTACHED_IMAGE~~$1";
	p = Pattern.compile(REGEX, Pattern.MULTILINE);
	// get a matcher object
	m = p.matcher(text); 
	text = m.replaceAll(REPLACE);

	// detect a table-plus
	text = text.replaceAll("\\{table-plus(.*)\\}\n*((.|\n)*?)\n*\\{table-plus\\}", "\n~~TABLEPLUS~~\n$2\n");

	/* detect a table-plus with headers */
	text = text.replaceAll("~~TABLEPLUS~~\n*([|][|].*[|][|])\\s*\n((\\|.*\n)+)\n", "\n<!-- table start -->\n~~TABLESTART~~ border=1  width=\"100%\" cellspacing=\"0\" cellpadding=\"4\" style=\"border-color:#eee\" class=\"wikitable sortable\" \n<!-- header row start -->\n~~HEADERROW~~$1~~HEADEREND~~\n<!-- header row end -->\n$2\n~~TABLEEND~~<!-- table end -->\n\n<br/>\n");

	/* detect a table with headers with split lines within same cell */
	//  text = text.replaceAll("([|][|].*[|][|])\\s*\n(([|][^|}-][^|]+\n[^|]+?[|]\\s*\n)+)", "\n<!-- table start -->\n~~TABLESTART~~ border=1  width=\"100%\" cellspacing=\"0\" cellpadding=\"4\" style=\"border-color:#eee\" class=\"wikitable sortable\"\n<!-- header row start -->\n~~HEADERROW~~$1~~HEADEREND~~\n<!-- header row end -->\n$2\n~~TABLEEND~~<!-- table end -->\n\n<br/>\n");
	REGEX = "([|][|].*[|][|])\\s*\n(([|][^|\\}-][^|]+\n[^|]+?[|]\\s*\n)+)";
	REPLACE =  "\n<!-- table start -->\n~~TABLESTART~~ border=1  width=\"100%\" cellspacing=\"0\" cellpadding=\"4\" style=\"border-color:#eee\" class=\"wikitable sortable\"\n<!-- header row start -->\n~~HEADERROW~~$1~~HEADEREND~~\n<!-- header row end -->\n$2\n~~TABLEEND~~<!-- table end -->\n\n<br/>\n";
	p = Pattern.compile(REGEX, Pattern.UNIX_LINES);
	m = p.matcher(text); 
	text = m.replaceAll(REPLACE);
	/* detect a table with headers */
	REGEX = "\n([|][|].*[|][|])\\s*\n((\\|.*\n)+)\n*";
	REPLACE = "\n<!-- table start -->\n~~TABLESTART~~ border=1  width=\"100%\" cellspacing=\"0\" cellpadding=\"4\" style=\"border-color:#eee\" class=\"wikitable sortable\" \n<!-- header row start -->\n~~HEADERROW~~$1~~HEADEREND~~\n<!-- header row end -->\n$2\n~~TABLEEND~~<!-- table end -->\n\n<br/>\n";
	p = Pattern.compile(REGEX, Pattern.UNIX_LINES);
	m = p.matcher(text); 
	text = m.replaceAll(REPLACE);
	//	  text = text.replaceAll("\n([|][|].*[|][|])\\s*\n(\\|.*\n)+)\n*", "\n<!-- table start -->\n~~TABLESTART~~ border=1  width=\"100%\" cellspacing=\"0\" cellpadding=\"4\" style=\"border-color:#eee\" class=\"wikitable sortable\" \n<!-- header row start -->\n~~HEADERROW~~$1~~HEADEREND~~\n<!-- header row end -->\n$2\n~~TABLEEND~~<!-- table end -->\n\n<br/>\n");

	/* Create table elements in header row*/
	text = text.replaceAll("[|][|]", "!!");

	/* Clean up beginning of header row */
	text = text.replaceAll("\n~~HEADERROW~~\\!\\!", "\n~~HEADERROW~~");

	/* Clean up end of header row */
	text = text.replaceAll("\\!\\!~~HEADEREND~~\n", "~~HEADEREND~~\n");


	text = text.replaceAll("[|] '''\\s*\\n", "|\n");

	// external links
	text = text.replaceAll("\\[(http:\\/\\/[^\\]|]+)\\]", "[$1 $1]");
	text = text.replaceAll("\\[(https:\\/\\/[^\\]|]+)\\]", "[$1 $1]");
	text = text.replaceAll("\\[\\[(http:\\/\\/[^\\]|]+)\\|([^\\]|]+)\\]\\]", "[$1 $2]");
	text = text.replaceAll("\\[\\[(https:\\/\\/[^\\]|]+)\\|([^\\]|]+)\\]\\]", "[$1 $2]");
	text = text.replaceAll("\\[([^\n|]+?)[|]\\s*(https*)([^\n]+?)\\]", "~~LINKSTART~~$2$3 $1~~LINKEND~~");

	/* Header links */
	text = text.replaceAll("\\[#(.+)\\]", "~~LINKSTART~~#$1~~LINKEND~~");
	
	// internal links
	text = text.replaceAll("\\[([^\n|]+?)[|]([^\n]+?)\\]", "~~LINKSTART~~$2~~LINKSEPARATOR~~$1~~LINKEND~~");
	text = text.replaceAll("\\[(BeanDev|MODDOCS):(.+?)\\]", "~~LINKSTART~~$2~~LINKEND~~");
	text = text.replaceAll("\\[([^\\]]*)+\\]", "~~LINKSTART~~$1~~LINKEND~~");
	

	/* detect regular rows that haven"t been detected yet*/
	// ISSUE WITH THIS LINE
	text = text.replaceAll("\n(([|][^|\n}-][^|}]*)+)[|]", "\n~~ROWSTART~~$1");


	/* Internal links with line break*/
	text = text.replaceAll("\\[([^\\]|])?\n([^\\]|])?]\n", "~~LINKSTART~~$1$2~~LINKEND~~<br>");

	/* Internal links without break */
	text = text.replaceAll("\\[([^\\]|]*)?\n([^\\]|])?]", "~~LINKSTART~~$1$2~~LINKEND~~");

	/* detect cells */
	//ISSUE WITH THIS LINE
	text = text.replaceAll("[|]([^|\n}-][^|\n}]*)", "\n~~CELLSTART~~$1");
	// Fix except for files with blank cells
	text = text.replaceAll("~~CELLSTART~~\\W\n", "~~ROWSTART~~");
	text = text.replaceAll("~~CELLSTART~~([^|]*?)[|]", "\n~~CELLSTART~~$1");
	text = text.replaceAll("^[|]-\n[|] '''", "|- style=\"background-color:#f0f0f0;\"\n| \'\'\'");

	// formatting
	/*text = text.replace("*(?!*)(.+?)*(?!*)", "'''$1'''");*/
	text = text.replaceAll("(\\W)\\*([^\n*]+?)\\*(\\W)", "$1'''$2'''$3");
	text = text.replaceAll("(\\W)_([\\w][^\n]*?[\\w])_(\\W)", "$1''$2''$3");

	// headings
	REGEX = "^h1. (.+)$";
	REPLACE = "= $1 =";
	p = Pattern.compile(REGEX, Pattern.MULTILINE);
	m = p.matcher(text); 
	text = m.replaceAll(REPLACE);
	REGEX = "^h2. (.+)$";
	REPLACE = "== $1 ==";
	p = Pattern.compile(REGEX, Pattern.MULTILINE);
	// get a matcher object
	m = p.matcher(text); 
	text = m.replaceAll(REPLACE);
	REGEX = "^h3. (.+)$";
	REPLACE = "=== $1 ===";
	p = Pattern.compile(REGEX, Pattern.MULTILINE);
	// get a matcher object
	m = p.matcher(text); 
	text = m.replaceAll(REPLACE);
	REGEX = "^h4. (.+)$";
	REPLACE = "==== $1 ====";
	p = Pattern.compile(REGEX, Pattern.MULTILINE);
	// get a matcher object
	m = p.matcher(text); 
	text = m.replaceAll(REPLACE);
	REGEX = "^h5. (.+)$";
	REPLACE = "===== $1 =====";
	p = Pattern.compile(REGEX, Pattern.MULTILINE);
	// get a matcher object
	m = p.matcher(text); 
	text = m.replaceAll(REPLACE);
	REGEX = "^h6. (.+)$";
	REPLACE = "====== $1 ======";
	p = Pattern.compile(REGEX, Pattern.MULTILINE);
	// get a matcher object
	m = p.matcher(text); 
	text = m.replaceAll(REPLACE);

	/* cleanup */
	text = text.replaceAll("~~LINKSEPARATOR~~", "|");
	text = text.replaceAll("~~STARTBRACKET~~", "[");
	text = text.replaceAll("~~LINKSTART~~", "[[");
	text = text.replaceAll("~~ENDBRACKET~~", "]");
	text = text.replaceAll("~~LINKEND~~", "]]");
	text = text.replaceAll("~~BACKSLASH~~", "\\");
	text = text.replaceAll("~~TABLESTART~~", "{|");
	text = text.replaceAll("~~TABLEEND~~", "|}");
	text = text.replaceAll("~~TABLEPLUS~~", "");
	text = text.replaceAll("~~HEADERROW~~", "!");
	text = text.replaceAll("~~HEADEREND~~", "");
	text = text.replaceAll("~~ROWSTART~~", "|-");
	text = text.replaceAll("\n+~~CELLSTART~~", "\n| ");
	text = text.replaceAll("~~STAR~~", "*");

	/* clean up multiple newlines */
	text = text.replaceAll("\n+[|][\\}]", "\n|\\}");
	text = text.replaceAll("\n+[|][-]\n+[|]", "\n|-\n|");
	text = text.replaceAll("\n[|][-]\n[|][-]\n", "\n|-\n");
	text = text.replaceAll("[\n\\s]+[{][|]", "\n{|");
	text = text.replaceAll("[\n\\s]+[!]([^!])", "\n!$1");
	text = text.replaceAll("\n[|]\n+[|]([^\\}-])", "\n|$1");  
	
	/* Change image tags to metadata compatible tags*/
	text = text.replaceAll("~~ATTACHED_IMAGE~~images/(.+)", "[[File:$1]]");
	
	/* fix broken table headers */
	text = text.replaceAll("<!-- header row end -->\n\\|", "<!-- header row end -->\n\\|-\n\\|");
	
	return text;
    }

    /**
     * Retrieve File list from loki directory site
     * @param URL - The URL as a String that points to the loki directory
     * @return The list of files as a List<String>
     * @throws IllegalStateException
     * @throws IOException
     */
    private static List<String> getFileList(String URL) throws IllegalStateException, IOException{
    	if (URL==null){
    		throw new IllegalArgumentException("Inputted URL is null");
    	}
	List <String> matches = new ArrayList <String> ();
	List <Pattern> patterns = new ArrayList <Pattern> ();
	BufferedReader buf = null;
	String match = null;
	patterns.add (Pattern.compile ("<a\\s*(href=)?('|\")[^'\"]+('|\") class=('|\")p('|\")>"));
	HttpClient client = new DefaultHttpClient();
	HttpGet httpget = new HttpGet(URL);
	HttpResponse response = client.execute(httpget);
	HttpEntity entity = response.getEntity();
	if (entity != null) {
	    try {
		InputStream inputStream = (InputStream) entity.getContent ();
		InputStreamReader isr = new InputStreamReader (inputStream);
		buf = new BufferedReader (isr);
		String str = null;
		while ((str = buf.readLine ()) != null){
		    for (Pattern p : patterns){
			Matcher m = p.matcher (str);
			while (m.find ()) {
			    match = m.group();
			    match = match.replace("<a href=\"","");
			    match = match.replace("\" class=\"p\">","");
			    matches.add (match);
			}
		    }
		} 
	    }
	    finally{
		buf.close();
	    }
	}
	return matches;
    }

    /**
     * Retrieve confluence file from loki as String
     * @param URL - The URL to a confluence file
     * @return String - The confluence file parsed as a string
     * @throws IllegalStateException
     * @throws IOException
     */
    private static String getConfluence(String URL) throws IllegalStateException, IOException{
    	if (URL==null){
    		throw new IllegalArgumentException("Inputted URL is null");
    	}
	HttpClient client = new DefaultHttpClient();
	HttpGet httpget = new HttpGet(URL);
	HttpResponse response = client.execute(httpget);
	HttpEntity entity = response.getEntity();
	InputStream inputStream = (InputStream) entity.getContent ();
	return IOUtils.toString(inputStream);
    }
    
    /**
     * Downloads all files found in the Resource directory on Loki
     * @param url - The base URL as a String to the loki resource directory
     * @param Resources - Passes a List<String> containing the resource file list
     * @param Directory - The local directory the files should be saved in
     */
    private static void downloadResources(String url, List<String> Resources, String Directory){
    	if (url==null||url.equalsIgnoreCase("")){
    		throw new IllegalArgumentException("Inputted URL is null");
    	}
    	if (Directory==null||Directory.equalsIgnoreCase("")){
    		throw new IllegalArgumentException("Inputed Directory is null");
    	}
    try {
	   String fileName = "";
	   for(int i = 0; i < Resources.size();i++){
	    fileName = Resources.get(i);
	    URL u = new URL(url+fileName);
	    fileName = fileName.replaceAll(SPACE_SYMBOL, " ");
	    System.out.println(" Downloading: " + fileName);
	    FileUtils.copyURLToFile(u, new File(Directory+fileName));
	   }
	   
	   
	  } catch(IOException e) {
	   e.printStackTrace();
	  }
    }

    private static void fillPropsWithArgs(String[] args, Properties myProps) throws ParseException{
    	Options options = new Options();
    	options.addOption("v", true, "version number");
    	options.addOption("s", true, "confluence source");
    	options.addOption("u", true, "URL");
    	options.addOption("f", true, "local file path");
    	
    	CommandLineParser parser = new PosixParser();
    	CommandLine cmd = parser.parse( options, args);
    	
    	// get option values
    	String version = cmd.getOptionValue("v");
    	String source = cmd.getOptionValue("s");
    	String URL = cmd.getOptionValue("u");
    	String file = cmd.getOptionValue("f");

    	if(version != null) {
    	    myProps.put("version", version);
    	}
    	if(source != null) {
    	    myProps.put("source", source);
    	}
    	if(URL != null) {
    	    myProps.put("URL", URL);
    	}
    	if(file != null) {
    	    myProps.put("file", file);
    	}
    	
    }
    
    /**
     * @param args - Command line arguments -s Source and -v Version
     * @throws IOException 
     * @throws ParseException  
     */
    public static void main(String[] args) throws IOException, ParseException {
	// TODO Auto-generated method stub
	File Props = new File("convert.properties");
	if (!Props.exists()) {
	    System.out.println("Please ensure that there is a valid Properties file.");
	    System.exit(0);
	}
	//Load from convert.properties
	//-------------------------------------------------
	Properties myProps = new Properties();
	FileInputStream MyInputStream = new FileInputStream(Props);
	myProps.load(MyInputStream);        
	String myPropValue = myProps.getProperty("propKey");
	String key = "";
	String value = "";
	for (Map.Entry<Object, Object> propItem : myProps.entrySet())
	{
	    key = (String) propItem.getKey();
	    value = (String) propItem.getValue();
	}
	MyInputStream.close();
	
	// Replace Properties values with values passed through command line.
	fillPropsWithArgs(args,myProps);
	
	
	// Main converting method
	convertFiles(init(myProps));

    }
}

