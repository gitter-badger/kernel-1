/**
 * This file is part of the CRISTAL-iSE kernel.
 * Copyright (c) 2001-2014 The CRISTAL Consortium. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 3 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; with out even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 *
 * http://www.fsf.org/licensing/licenses/lgpl.html
 */
package org.cristalise.kernel.scripting;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.cristalise.kernel.common.ObjectNotFoundException;
import org.cristalise.kernel.entity.agent.Job;
import org.cristalise.kernel.entity.proxy.AgentProxy;
import org.cristalise.kernel.entity.proxy.ItemProxy;
import org.cristalise.kernel.process.Gateway;
import org.cristalise.kernel.utils.LocalObjectLoader;
import org.cristalise.kernel.utils.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;


/**************************************************************************
 *
 * $Revision: 1.25 $
 * $Date: 2005/10/05 07:39:37 $
 *
 * Copyright (C) 2003 CERN - European Organization for Nuclear Research
 * All rights reserved.
 **************************************************************************/
public class Script
{
    String mScript = "";
    CompiledScript mCompScript = null;
    String mName;
    Integer mVersion;
    HashMap<String, Parameter> mInputParams = new HashMap<String, Parameter>();
    HashMap<String, Parameter> mAllInputParams = new HashMap<String, Parameter>();
    HashMap<String, Parameter> mOutputParams = new HashMap<String, Parameter>();
    ArrayList<Script> mIncludes = new ArrayList<Script>();
    ScriptEngine engine;
    ScriptContext context;

    /** For testing. Parses a given script xml, instead of loading it from Items.
     * @param xml
     * @throws ScriptParsingException
     * @throws ParameterException
     */
    public Script(String name, int version, String xml) throws ScriptParsingException, ParameterException {
    	mName = name; mVersion = version;
    	parseScriptXML(xml);
    }
    /**
     * Loads script xml and parses it for script source, parameters and output specifications.
     * First tries to load the script from resource path /scriptFiles/scriptName_scriptVersion.xml
     * If not found tries to find item at /desc/ScriptDesc/scriptName and load Viewpoint scriptVersion from it.
     *
     * For the specification of script xml, see the Script schema from resources.
     *
     * @param scriptName - name of the script
     * @param scriptVersion - named version of the script (must be numbered viewpoint)
     * @throws ScriptParsingException - when script not found (ScriptLoadingException) or xml is invalid (ScriptParsingException)
     */
    public Script(String scriptName, int scriptVersion, ScriptContext context) throws ScriptingEngineException
    {
    	this(scriptName, scriptVersion);
        this.context = context;
    }
    
    public Script(String scriptName, int scriptVersion) throws ScriptingEngineException
    {
        mName = scriptName;
        mVersion = scriptVersion;
        if (!scriptName.equals("")) 
        	loadScript(mName, mVersion);
    }

    /**
     * Creates a script executor for the supplied expression, bypassing the xml parsing bit
     * Output class is forced to an object.
     */
    public Script(String lang, String expr, Class<?> returnType) throws ScriptingEngineException
    {
        mName = "<expr>";
        setScriptEngine(lang);
        mVersion = null;
        addOutput(null, returnType);
        setScript(expr);
    }
    
    /**
     * Creates a script executor requiring an agent to be set. Used by module event scripts.
     * 
     * @param lang - script language
     * @param name - script name for debugging
     * @param expr - the script to run
     * @param agent - the agentproxy to pass into the script as 'agent'
     * @throws ScriptingEngineException
     */
    public Script(String lang, String name, String expr, AgentProxy agent) throws ScriptingEngineException
    {
    	this(lang, expr, Object.class);
    	mName = name;
    	addInputParam("agent", AgentProxy.class);
    	setInputParamValue("agent", agent);
    }

    public Script(String lang, String expr) throws ScriptingEngineException
    {
        this(lang, expr, Object.class);
    }

    public Script(ItemProxy object, AgentProxy subject, Job job) throws ScriptingEngineException
    {
        this(job.getActPropString("ScriptName"), getScriptVersion(job));
        // set environment - this needs to be well documented for script developers
        if (!mInputParams.containsKey("item"))
        	addInputParam("item", ItemProxy.class);
        setInputParamValue("item", object);

        if (!mInputParams.containsKey("agent"))
        	addInputParam("agent", AgentProxy.class);
        setInputParamValue("agent", subject);

        if (!mInputParams.containsKey("job"))
        	addInputParam("job", Job.class);
        setInputParamValue("job", job);

        if (!mOutputParams.containsKey("errors"))
        	addOutput("errors", ErrorInfo.class);
    }
    
    public static int getScriptVersion(Job job) {
    	String verStr = job.getActPropString("ScriptVersion");
    	if (verStr != null)
    		return Integer.parseInt(verStr);
    	else
    		return -1;
    }
    
    /**
     * For consoles
     * 
     * @param lang - script language
     * @param agent - AgentProxy of the console user
     * @param out - the output PrintStream for reporting results that don't go to the log
     */
    public Script(String lang, AgentProxy agent, PrintStream out) throws Exception {
    	setScriptEngine(lang);
    	Bindings beans = context.getBindings(ScriptContext.ENGINE_SCOPE);
        beans.put("storage", Gateway.getStorage());
        beans.put("db", Gateway.getStorage().getDb());
        beans.put("proxy", Gateway.getProxyManager());
        beans.put("lookup", Gateway.getLookup());
        beans.put("orb", Gateway.getORB());
        beans.put("agent", agent);
        beans.put("output", out);
        PrintWriter output = new PrintWriter(out);
        context.setWriter(output);
        context.setErrorWriter(output);
        HashMap<String, String> consoleScripts = Gateway.getResource().getAllTextResources("textFiles/consoleScript."+lang+".txt");
		for (String ns : consoleScripts.keySet()) {
			try {
				engine.put(ScriptEngine.FILENAME, ns+" init script");
				engine.eval(consoleScripts.get(ns));
			} catch (ScriptException ex) {
				out.println("Exception parsing console script for "+(ns==null?"kernel":ns+" module"));
				ex.printStackTrace(out);
			}
		}
		addOutput(null, Object.class);

    }

    public void setScriptEngine(String lang) throws ScriptingEngineException {
    	engine = new ScriptEngineManager(getClass().getClassLoader()).getEngineByName(lang);
    	if (engine==null)
    		throw new ScriptingEngineException("No script engine for '"+lang+"' found.");
    	Bindings beans = engine.createBindings();
    	context = new SimpleScriptContext();
    	context.setBindings(beans, ScriptContext.ENGINE_SCOPE);
    	engine.setContext(context);
    }
    
    private void loadScript(String scriptName, int scriptVersion) throws ScriptingEngineException
    {
        try
        {
            mName = scriptName;
            mVersion = scriptVersion;
            parseScriptXML(LocalObjectLoader.getScript(scriptName, scriptVersion));
        }
        catch (ObjectNotFoundException e)
        {
            throw new ScriptingEngineException("Script '"+scriptName+"' not found");
        }
    }

    /**
     * Extracts script data from script xml.
     *
     * @param scriptXML
     * @throws ScriptParsingException - when script is invalid
     */
    private void parseScriptXML(String scriptXML) throws ScriptParsingException, ParameterException
    {
        Document scriptDoc = null;

        // get the DOM document from the XML
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try
        {
            DocumentBuilder domBuilder = factory.newDocumentBuilder();
            scriptDoc = domBuilder.parse(new InputSource(new StringReader(scriptXML)));
        }
        catch (Exception ex)
        {
            throw new ScriptParsingException("Error parsing Script XML : " + ex.toString());
        }

        Element root = scriptDoc.getDocumentElement();
        NodeList scriptNodes = root.getChildNodes();
        for (int i = 0; i < scriptNodes.getLength(); i++)
        {
            Element currentParam;
            String paramName;

            try
            {
                currentParam = (Element) scriptNodes.item(i);
            }
            catch (ClassCastException ex)
            {
                // not an element, skip
                continue;
            }
            paramName = currentParam.getTagName();
            Logger.msg(9, "Script.parseScriptXML() - Found element " + paramName);

            // Process script parameters

            // input parameter
            if (paramName.equals("param"))
            {
                if (!(currentParam.hasAttribute("name") && currentParam.hasAttribute("type")))
                    throw new ScriptParsingException("Script Input Param incomplete, must have name and type");
                addInputParam(currentParam.getAttribute("name"), currentParam.getAttribute("type"));
            }

            //load output type
            else if (paramName.equals("output"))
            {
                if (!currentParam.hasAttribute("type"))
                    throw new ScriptParsingException("Script Output declaration incomplete, must have type");
                addOutput(currentParam.getAttribute("name"), currentParam.getAttribute("type"));
            }

            //load any included scripts
            else if (paramName.equals("include"))
            {
                if (!(currentParam.hasAttribute("name") && currentParam.hasAttribute("version")))
                    throw new ScriptParsingException("Script include declaration incomplete, must have name and version");
                String includeName = currentParam.getAttribute("name");
                String includeVersion =  currentParam.getAttribute("version");
                try {
                    Script includedScript = new Script(includeName, Integer.parseInt(includeVersion), context);
                    mIncludes.add(includedScript);
                    for (Parameter includeParam : includedScript.getInputParams().values()) {
                        addIncludedInputParam(includeParam.getName(), includeParam.getType());
                    }
                } catch (NumberFormatException e) {
                    throw new ScriptParsingException("Invalid version in imported script name:'"+includeName+"', version:'"+includeVersion+"'");
                } catch (ScriptingEngineException e) {
                    throw new ScriptParsingException("Error parsing imported script '"+includeName+"', version '"+includeVersion+"': "+e.getMessage());
                }

            }
            //load Script
            else if (paramName.equals("script"))
            {
                if (!currentParam.hasAttribute("language"))
                    throw new ScriptParsingException("Script data incomplete, must specify scripting language");
                Logger.msg(6, "Script.parseScriptXML() - Script Language: " + currentParam.getAttribute("language"));
                try {
                	setScriptEngine(currentParam.getAttribute("language"));
                } catch (ScriptingEngineException ex) {
                	throw new ScriptParsingException(ex.getMessage());
                }

                // get script source
                NodeList scriptChildNodes = currentParam.getChildNodes();
                if (scriptChildNodes.getLength() != 1)
                    throw new ScriptParsingException("More than one child element found under script tag. Script characters may need escaping - suggest convert to CDATA section");
                if (scriptChildNodes.item(0) instanceof Text)
                    setScript(((Text) scriptChildNodes.item(0)).getData());
                else
                    throw new ScriptParsingException("Child element of script tag was not text");
                Logger.msg(6, "Script.parseScriptXML() - script:" + mScript);
            }
        }
    }

    protected void addInputParam(String name, String type) throws ParameterException
    {
    	try
        {
            addInputParam(name, Gateway.getResource().getClassForName(type));
        }
        catch (ClassNotFoundException ex)
        {
            throw new ParameterException("Input parameter " + name + " specifies class " + type + " which was not found.");
        }
    }
    
    protected void addInputParam(String name, Class<?> type) throws ParameterException
    {
        Parameter inputParam = new Parameter(name, type);

        

        Logger.msg(6, "ScriptExecutor.parseScriptXML() - declared parameter " + name + " (" + type + ")");
        //add parameter to hashtable
        mInputParams.put(inputParam.getName(), inputParam);
        mAllInputParams.put(inputParam.getName(), inputParam);

    }

    protected void addIncludedInputParam(String name, Class<?> type) throws ParameterException
    {
        // check if we already have it
        if (mAllInputParams.containsKey(name)) {
            Parameter existingParam = mAllInputParams.get(name);
            // check the types match
            if (existingParam.getType() == type)
                return; // matches
            else // error
                throw new ParameterException("Parameter conflict. Parameter'"+name+"' is declared as  "
                        +existingParam.getType().getName()+" is declared in another script as "+type.getName());
        }

        Parameter inputParam = new Parameter(name);
        inputParam.setType(type);

        //add parameter to hashtable
        mAllInputParams.put(inputParam.getName(), inputParam);

    }

    protected void addOutput(String name, String type) throws ParameterException
    {
        try
        {
            addOutput(name, Gateway.getResource().getClassForName(type));
        }
        catch (ClassNotFoundException ex) {
            throw new ParameterException("Output parameter " + name + " specifies class " + type + " which was not found.");
        }
    }

    protected void addOutput(String name, Class<?> type) throws ParameterException
    {
        String outputName = name;

        Parameter outputParam = new Parameter(name, type);

        if (mOutputParams.containsKey(outputName))
        	throw new ParameterException("Output parameter '"+outputName+"' declared more than once.");

        mOutputParams.put(outputName, outputParam);
        
    }

    /**
     * Gets all declared parameters
     * @return HashMap of String (name), org.cristalise.kernel.scripting.Parameter (param)
     * @see org.cristalise.kernel.scripting.Parameter
     */
    public HashMap<String, Parameter> getInputParams()
    {
        return mInputParams;
    }

    /**
     * Gets all declared parameters, including those of imported scripts
     * @return HashMap of String (name), org.cristalise.kernel.scripting.Parameter (param)
     * @see org.cristalise.kernel.scripting.Parameter
     */
    public HashMap<String, Parameter> getAllInputParams()
    {
        return mAllInputParams;
    }

    /**
     * Submits an input parameter to the script. Must be declared by name and type in the script XML.
     *
     * @param name - input parameter name from the script xml
     * @param value - object to use for this parameter
     * @throws ParameterException - name not found or wrong type
     */
    public void setInputParamValue(String name, Object value) throws ParameterException
    {
        Parameter param = mInputParams.get(name);

        if (!mAllInputParams.containsKey(name))
            throw new ParameterException("Parameter " + name + " not found in parameter list");

        if (param != null) { // param is in this script
            if (value.getClass() != param.getType())
                throw new ParameterException(
                    "Parameter " + name + " is wrong type \n" + "Required: " + param.getType().toString() + "\n" + "Supplied: " + value.getClass().toString());
            context.getBindings(ScriptContext.ENGINE_SCOPE).put(name, value);
            Logger.msg(7, "Script.setInputParamValue() - " + name + ": " + value.toString());
            param.setInitialised(true);
        }

        // pass param down to child scripts
        for (Script importScript : mIncludes) {
            importScript.setInputParamValue(name, value);
        }
    }

    /**
     * Executes the script with the submitted parameters. All declared input parametes should have been set first.
     *
     * @return The return value depends on the way the output type was declared in the script xml.
     * <ul><li>If there was no output class declared then null is returned
     * <li>If a class was declared, but not named, then the object returned by the script is checked
     * to be of that type, then returned.
     * <li>If the output value was named and typed, then an object of that class is created and
     * passed to the script as an input parameter. The script should set this before it returns.
     * </ul>
     * @throws ScriptingEngineException - input parameters weren't set, there was an error executing the script, or the output was invalid
     */
    public Object execute() throws ScriptingEngineException
    {

        // check input params
        StringBuffer missingParams = new StringBuffer();
        for (Parameter thisParam : mInputParams.values()) {
            if (!thisParam.getInitialised())
                missingParams.append(thisParam.getName()).append("\n");
        }
        // croak if any missing
        if (missingParams.length() > 0)
            throw new ScriptingEngineException("Execution aborted, the following declared parameters were not set: \n" + missingParams.toString());

        for (Parameter outputParam : mOutputParams.values()) {
        	if (outputParam.getName() == null || outputParam.getName().length()==0) continue; // If the name is null then it's the return type. don't pre-register it
            Logger.msg(8, "Script.setOutput() - Initialising output bean '" + outputParam.getName() + "'");
            Object emptyObject;
			try {
				emptyObject = outputParam.getType().newInstance();
			} catch (Exception e) {
				emptyObject = null;
			}
			context.getBindings(ScriptContext.ENGINE_SCOPE).put(outputParam.getName(), emptyObject);

		}

        // execute the child scripts
        for (Script importScript : mIncludes) {
        	if (Logger.doLog(8))
        		Logger.msg(8, "Import script:\n"+importScript.mScript);
            importScript.execute();
        }

        // run the script
        Object returnValue = null;
        try
        {
            Logger.msg(7, "Script.execute() - Executing script");
        	if (Logger.doLog(8))
        		Logger.msg(8, "Script:\n"+mScript);
            
            if (engine == null)
            	throw new ScriptingEngineException("Script engine not set. Cannot execute scripts.");
            engine.put(ScriptEngine.FILENAME, mName);
            if (mCompScript != null)
            	returnValue = mCompScript.eval();
            else
            	returnValue = engine.eval(mScript);
            Logger.msg(7, "Script.execute() - script returned \"" + returnValue + "\"");
        }
        catch (Throwable ex)
        {
            throw new ScriptingEngineException("Error executing script: " + ex.getMessage());
        }
        
        // if no outputs are defined, return null
        if (mOutputParams.size() == 0) {
        	Logger.msg(4, "Script.execute() - No output params. Returning null.");
        	return null;
        }
        
		HashMap<String, Object> outputs = new HashMap<String, Object>();
		
        for (Parameter outputParam : mOutputParams.values()) {
        	String outputName = outputParam.getName();
        	Object outputValue;
        	if (outputName == null || outputName.length()==0)
        		outputValue = returnValue;
        	else
        		outputValue = context.getBindings(ScriptContext.ENGINE_SCOPE).get(outputParam.getName());
        	Logger.msg(4, "Script.execute() - Output parameter "+outputName+"="+(outputValue==null?"null":outputValue.toString()));
        	
       		// check the class
       		if (outputValue!=null && !(outputParam.getType().isInstance(outputValue)))
       			throw new ScriptingEngineException(
                     "Script output "+outputName+" was not null or instance of " + outputParam.getType().getName() + ", it was a " + outputValue.getClass().getName());    
                  		
       		Logger.msg(8, "Script.execute() - output "+outputValue);
       		if (mOutputParams.size() == 1) {
       			Logger.msg(6, "Script.execute() - only one parameter, returning "+(outputValue==null?"null":outputValue.toString()));
       			return outputValue;
       		}
       		outputs.put(outputParam.getName(), outputValue);
		}
        
        return outputs;
    }
    
    public void setScript(String script) throws ScriptParsingException {
    	mScript = script;
        if (engine instanceof Compilable) {
			try {
				Logger.msg(1, "Compiling script "+mName);
	            engine.put(ScriptEngine.FILENAME, mName);
				mCompScript = ((Compilable)engine).compile(mScript);
			} catch (ScriptException e) {
				throw new ScriptParsingException(e.getMessage());
			}
        }
    }
    
    static public void main(String[] args) {
    	for(ScriptEngineFactory sef: new ScriptEngineManager().getEngineFactories()) {
    		System.out.println(sef.getEngineName()+" v"+sef.getEngineVersion()+" using "+sef.getLanguageName()+" v"+sef.getLanguageVersion()+" "+sef.getNames());
    	}
    }
}
