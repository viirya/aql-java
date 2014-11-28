package com.aerospike.aql;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.aerospike.client.AerospikeException;
import com.aerospike.client.Key;
import com.aerospike.client.Log.Level;
import com.aerospike.client.Record;
import com.aerospike.client.query.RecordSet;
import com.aerospike.client.query.ResultSet;

public class AQLConsole implements IResultReporter {
	boolean cancelled = false;
	int errors = 0;
	private ViewFormat format = ViewFormat.TABLE;
	Console systemConsole = System.console();
	boolean useSystemConsole = false;
	Object lastResult = null;
	
	public AQLConsole() {
		this.useSystemConsole = (this.systemConsole != null);
	}

	public void printf(String message, Object... args){
		if (useSystemConsole)
			systemConsole.printf(message, args);
		else {
			System.out.printf(message, args);
		}
	}

	public void print(String message){
		if (useSystemConsole)
			systemConsole.printf(message);
		else {
			System.out.print(message);
		}
	}

	public void println(){
		if (useSystemConsole)
			systemConsole.printf("\n");
		else {
			System.out.println();
		}
	}
	
	public void println(Object object){
		if (useSystemConsole)
			systemConsole.printf(object.toString() + "\n");
		else {
			System.out.println(object.toString());
		}
	}
	
	public void println(String message, Object... args){
		if (useSystemConsole)
			systemConsole.printf(message + "\n", args);
		else {
			System.out.println(String.format(message, args));
		}
	}


	public String readLine(){
		if (useSystemConsole)
			return systemConsole.readLine();
		else {
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
			String line = "";
			try {
				line = bufferedReader.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return line;
		}
	}

	@Override
	public void report(String message) {
		println(message);

	}

	@Override
	public void report(Key key, Record record, boolean clear) {
		report(key, record);
		
	}
	@Override
	public void report(Key key, Record record) {
		if (record != null){
			switch (this.format) {
			case JSON:
				println(recordJSON(record).toJSONString());
				break;
			case TABLE:
				List<Record> recordList = new ArrayList<Record>();
				Map<String, Integer> binList = new HashMap<String, Integer>();
				recordList.add(record);
				makeFieldMap(binList, record);
				printTableRecordList(recordList, binList);
				break;
			default: //TEXT:

				print("Record: ");
				if (key != null){
					print(key.toString() + " ");
				}
				for (String binName :record.bins.keySet()){
					String result = record.getValue(binName).toString();
					print(" bin="+binName +" value="+ result);
				}
				println();
				break;
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private JSONObject recordJSON(Record record){
		JSONObject jObject = new JSONObject(record.bins);
		jObject.put("generation", record.generation);
		jObject.put("expiration", record.expiration);
		return jObject;
	}

	@SuppressWarnings("unchecked")
	private JSONObject keyJSON(Key key){
		JSONObject jKey = new JSONObject();
		jKey.put("namespace", key.namespace);
		jKey.put("set",key.setName);
		jKey.put("key", key.userKey);
		return jKey;
	}

	@Override
	public void report(Record record) {
		report(null, record);

	}

	@Override
	@SuppressWarnings("unchecked")
	public void report(RecordSet recordSet) {
		switch (this.format) {
		case JSON:
			try {
				int count = 0;
				JSONArray recordList = new JSONArray();
				while (recordSet.next()) {
					Key key = recordSet.getKey();
					JSONObject jrecord = recordJSON(recordSet.getRecord());
					JSONObject jKey = keyJSON(key);
					JSONObject jrow = new JSONObject();
					jrow.put("key", jKey);
					jrow.put("record", jrecord);
					recordList.add(jrow);
					count ++;
				}
				if (count == 0) {
					println("No records returned.");			
				} else {
					println(recordList.toJSONString());
				}
			} catch (AerospikeException e) {
				e.printStackTrace();
			} finally {
				if (recordSet != null) {
					recordSet.close();
				}

			}
			break;
		case TABLE:
			try {
				int count = 0;
				List<Record> recordList = new ArrayList<Record>();
				Map<String, Integer> binList = new HashMap<String, Integer>();
				while (recordSet.next()) {
					Record record = recordSet.getRecord();
					recordList.add(record);
					makeFieldMap(binList, record);
					count ++;
					if (count % 50 == 0){
						printTableRecordList(recordList, binList);
						recordList.clear();
					}
				}
				if (recordList.size() > 0){
					printTableRecordList(recordList, binList);
				}
			} catch (AerospikeException e) {
				e.printStackTrace();
			} finally {
				if (recordSet != null) {
					recordSet.close();
				}
			}
			break;
		default: //TEXT:

			try {
				int count = 0;
				while (recordSet.next()) {
					Key key = recordSet.getKey();
					Record record = recordSet.getRecord();
					print("Record: " + key.toString());
					for (String binName :record.bins.keySet()){
						String result = record.getValue(binName).toString();
						print(" bin="+binName +" value="+ result);
					}
					println();
					count++;
				}
				if (count == 0) {
					println("No records returned.");			
				}
			} catch (AerospikeException e) {
				e.printStackTrace();
			} finally {
				if (recordSet != null) {
					recordSet.close();
				}

			}
			break;
		}
	}
	
	private Map<String, Integer> makeFieldMap(Map<String, Integer> fieldMap, Record record ){
		if (fieldMap == null)
			fieldMap = new HashMap<String, Integer>();
		Set<String> bins = record.bins.keySet();
		for (String bin : bins){
			Integer size = fieldMap.get(bin);
			Integer binValueSize = record.getValue(bin).toString().length();
			Integer binNameSize = bin.length();
			Integer fieldSize = Math.max(binNameSize, binValueSize);
			if (!fieldMap.containsKey(bin) || (size < fieldSize)) {
				fieldMap.put(bin, fieldSize);
			} 
		}
		return fieldMap;
	}
	
	private Map<String, Integer> makeFieldMap(Map<String, Integer> fieldMap,
			Map<String, String> element) {
		if (fieldMap == null)
			fieldMap = new HashMap<String, Integer>();
		Set<String> fields = element.keySet();
		for (String field : fields){
			Integer size = fieldMap.get(field);
			Integer fieldValueSize = element.get(field).toString().length();
			Integer fieldNameSize = field.length();
			Integer fieldSize = Math.max(fieldNameSize, fieldValueSize);
			if (!fieldMap.containsKey(field) || (size < fieldSize)) {
				fieldMap.put(field, fieldSize);
			} 
		}
		return fieldMap;
	}

	private void printTableRecordList(List<Record> recordList, Map<String, Integer> fieldMap){
		printTableHeader(fieldMap);
		for (Record record : recordList){	
			printTableEntry(record, fieldMap);
		}
		printTableSeperator(fieldMap);
	}	
	
	private void printTableHeader(Map<String, Integer> fieldMap){
		Set<Entry<String, Integer>> fields = fieldMap.entrySet();
		printTableSeperator(fieldMap);
		print("|");
		for (Entry<String, Integer> bin : fields){
			print(" ");
			printField(bin.getKey(), bin.getValue());
			print(" |");
		}
		println();
		printTableSeperator(fieldMap);
	}
	
	private void printTableEntry(Record record, Map<String, Integer> fieldMap){
		Set<Entry<String, Integer>> fields = fieldMap.entrySet();
		print("|");
		for (Entry<String, Integer> field : fields){
			print(" ");
			printField(record.getValue(field.getKey()).toString(), field.getValue());
			print(" |");
		}
		println();
	}
	
	private void printTableEntry(Map<String, String> element,
			Map<String, Integer> fieldMap) {
		Set<Entry<String, Integer>> fields = fieldMap.entrySet();
		print("|");
		for (Entry<String, Integer> field : fields){
			print(" ");
			printField(element.get(field.getKey()).toString(), field.getValue());
			print(" |");
		}
		println();
		
	}
	
	private void printField(String value, int width){
		print(value);
		for (int i = value.length(); i < width; i++ )
			print(" ");
	}
	
	private void printTableSeperator(Map<String, Integer> fieldMap){
		Set<Entry<String, Integer>> fields = fieldMap.entrySet();
		print("+");
		for (Entry<String, Integer> field : fields){
			print("-");
			for (int i = 0; i < field.getValue(); i++)
				print("-");
			print("-+");
		}
		println();
	}
	
	@Override
	public void report(Level level, String message) {
		switch (level){
		case DEBUG:
			println("DEBUG: " + message);
			break;
		case ERROR:
			System.err.println("ERROR: " + message);
			break;
		case WARN:
			println("WARN: " + message);
			break;
		case INFO:
			println("INFO: " + message);
			break;
		}

	}

	@Override
	public void report(String message, boolean clear) {
		this.report(message);

	}

	@Override
	public void report(Level level, String message, boolean clear) {
		this.report(level, message);

	}

	@Override
	public void report(Record record, boolean clear) {
		this.report(record);

	}

	@Override
	public void report(RecordSet recordSet, boolean clear) {
		this.report(recordSet);

	}

	@Override
	public void reportInfo(String inforMessage, String... seperators) {
		reportInfo(inforMessage, false, seperators);

	}

	@Override
	public void reportInfo(String inforMessage, boolean clear,
			String... seperators) {
		printInfo(inforMessage, seperators);
	}
	protected void printInfo(String infoString, String... seperators){
		if (infoString == null || infoString.isEmpty())
			return;
		if ( seperators == null || seperators.length == 0 ){
			println(infoString);
			return;
		}
		
		if (seperators.length == 3 ){
			Map<String, Map<String, String>> result = makeElementMap(infoString, seperators[0], seperators[1], seperators[2], "=");
			switch (this.format) {
			case JSON:
				println(formatJson(result));
				break;
			case TABLE:
				printTableMapList(result);
				break;
			default: //TEXT:
				println(result);
				break;
			}
			
			return;
		}
		if (seperators.length == 1 ){
			Map<String, String> result = makeValueMap(infoString, seperators[0], "=");
			switch (this.format) {
			case JSON:
				println(formatJson(result));
				break;
			case TABLE:
				printTableMap(result);
				break;
			default: //TEXT:
				println(result);
			}
			return;
		}
		
		println("WFT:"+infoString);
	}
	
	private void printTableMap(Map<String, String> infoMap){
		Map<String, Integer> fieldMap = new HashMap<String, Integer>();
		makeFieldMap(fieldMap, infoMap);
		printTableHeader(fieldMap);
		printTableEntry(infoMap, fieldMap);
		printTableSeperator(fieldMap);
	}	

	private void printTableMapList(Map<String, Map<String, String>> infoMap){
		Map<String, Integer> fieldMap = new HashMap<String, Integer>();
		Set<String> keys = infoMap.keySet();
		for (String element : keys){
			makeFieldMap(fieldMap, infoMap.get(element));
		}
		printTableHeader(fieldMap);
		for (String element : keys){
			printTableEntry(infoMap.get(element), fieldMap);
		}
		printTableSeperator(fieldMap);
	}	

	

	private Map<String, Map<String, String>> makeElementMap(String input, String elementSeperator, String keySeperator, String valueSeperator, String equator){
		Map<String, Map<String, String>> result = new HashMap<String, Map<String,String>>();
		String[] parts = input.split(elementSeperator);
		for (String element : parts){
			String[] chunks = element.split(keySeperator);
			String key = chunks[0];
			if (chunks.length >1){
				Map<String, String> value = makeValueMap(chunks[1], valueSeperator, equator);
				result.put(key, value);
			} else {
				result.put(key, null);
			}
			}
		return result;
	}
	
	private Map<String, String> makeValueMap(String input, String seperator, String equator){
		Map<String, String> result = new HashMap<String, String>();
		String[] parts = input.split(seperator);
		for (String element : parts){
			String[] chunks = element.split(equator);
			result.put(chunks[0], (chunks.length ==2) ? chunks[1] : chunks[0]);
		}
		return result;
	}
	
//	private String[] nameValueParts(String[] parts, boolean headerRow){
//		String[] nvs = new String[parts.length];
//		for (int i = 0; i < parts.length; i++) {
//			String[] nv = parts[i].split("=");
//			if (headerRow){
//				nvs[i] = nv[0];
//			} else if (nv.length > 1){
//				nvs[i] = nv[1];
//			}
//		}
//		return nvs;
//	}

	@Override
	public void cancel() {
		this.cancelled = true;
	}

	@Override
	public boolean isCancelled() {
		return this.cancelled;
		
	}

	@Override
	public void report(AerospikeException e) {
		println(String.format("Aerospike %s", e.getMessage()));
		//e.printStackTrace();
		
	}

	@Override
	public void report(ResultSet resultSet) {
		this.report(resultSet, false);
		
	}

	@Override
	public void report(ResultSet resultSet, boolean clear) {
		switch (this.format) {
		case JSON:
			break;
		case TABLE:
			break;
		default: //TEXT:
			try {
				int count = 0;
				while (resultSet.next()) {
					Object object = resultSet.getObject();
					count++;
					println(String.format("Result %d: %s", count, object.toString()));
				}
				if (count == 0) {
					println("No results returned.");			
				}
			}
			finally {
				resultSet.close();
			}
			break;
		}
	}

	@Override
	public void reportInfo(String[] inforMessages, String... seperators) {
		this.reportInfo(inforMessages, false, seperators);
		
	}

	@Override
	public void reportInfo(String[] inforMessages, boolean clear,
			String... seperators) {
		for (String message : inforMessages){
			this.printInfo(message, seperators);
		}
		
	}

	public String getStackTrace(Throwable throwable) {
		StringWriter sw = new StringWriter();
		throwable.printStackTrace(new PrintWriter(sw));
		return sw.toString();
	}

	@Override
	public void setViewFormat(ViewFormat format) {
		this.format = format;
	}
	
	private String formatJson(Object json){
		if (json instanceof List){
			JSONArray jArray = new JSONArray();
			jArray.addAll((Collection) json);
			return jArray.toJSONString();
		} else if (json instanceof Map){
			JSONObject jObject = new JSONObject((Map) json);
			return jObject.toJSONString();
		} else {
			return null;
		}
		
	}

}
