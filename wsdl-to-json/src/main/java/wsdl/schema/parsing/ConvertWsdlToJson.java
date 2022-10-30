package wsdl.schema.parsing;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;

import javax.xml.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.*;

import org.apache.commons.text.StringSubstitutor;

public class ConvertWsdlToJson {
	
	public static final String PATH = "/Users/I327667/eclipse-workspace-cx-suite-integration/convertion/src/main/resources/";
	public static final String TEMPLATE_COMPLEXTYPE_OBJECT = "attributeComplexTypeObject";
	public static final String TEMPLATE_COMPLEXTYPE_ARRAY = "attributeComplexTypeArray";
	public static final String TEMPLATE_SIMPLETYPE = "attributeSimpleType";
	public static final String TEMPLATE_SIMPLETYPE_ARRAY = "attributeSimpleTypeArray";
	public static final String TEMPLATE_SIMPLETYPE_DATETIME = "attributeSimpleTypeDateTime";
	public static final String TEMPLATE_MESSAGE = "message";
	public static final String OUTPUT_SCHEMA = "outputSchema";
	public static final String INPUT_FILE = "salesOrderErrorLog";
	public static final String VERIFY = "-verify";
	public static final String EXT_JSON = ".json";
	public static final String EXT_WSDL = ".wsdl";
	
	
	public static void main(String[] args) {
		
		try {
			
			DocumentBuilder builder = createDocumentBuilder();

	        Document document = builder.parse(new File(PATH + INPUT_FILE + EXT_WSDL));
	        
	        verifyDocument(document, System.out);  /* verify wsdl document*/
	        
	        System.out.println("-------start convertion-------");
	        
	        convertToJson(document);
	        
	        System.out.println("-----------finished-----------");
		}
		
		catch (Exception e) {
			e.printStackTrace();
		}
		
		System.out.println("\nHealth is ok!");

	}
	
	
	private static String convertToJson(Document document) throws Exception {
		
		/* Proceed if setup successful */
		if(initialSetup(document)) {
			
			System.out.println("start convertion\n\t***");
			
			NodeList rootNodeList = document.getDocumentElement().getChildNodes();
			
			Map<String, String> messageNameElementMap = getMessageNameTypeMap(rootNodeList);
			
			String targetNameSpace = messageNameElementMap.get("element")
					.substring(0,messageNameElementMap.get("element").indexOf(":"));
			String targetType = messageNameElementMap.get("element")
					.substring(messageNameElementMap.get("element").indexOf(":")+1,messageNameElementMap.get("element").length());
			
			String nameSpace = namespaceMap.entrySet().stream().anyMatch(k -> k.getKey().contains(targetNameSpace))
					? namespaceMap.get("xmlns:" + targetNameSpace): "";
			
			Node rootNode = getRootNodeForNamespace(rootNodeList,nameSpace);
			
			String jsonSchema = getJsonSchemaForRoot(rootNode,targetType);
			
			/* Print the json in outputSchema.json */
			try (PrintWriter out = new PrintWriter(PATH + OUTPUT_SCHEMA + EXT_JSON)) {
			    out.println(jsonSchema);
			}
			
			System.out.println("convertion successfull\n\t***");
			
			return jsonSchema;
			
		} else {
			
			throw new Exception("Setup Unsuccessful");
			
		}
		
	}


	private static String getJsonSchemaForRoot(Node node, String targetType) throws IOException {
		
		String result = "";
		String jsonMessage = new String(Files.readAllBytes(Paths.get(PATH + TEMPLATE_MESSAGE + EXT_JSON)));
		String messageType = "";
		
		for(int i=0; i<node.getChildNodes().getLength(); i++) {
			
			if(node.getChildNodes().item(i).hasAttributes()) {
				
				Map<String, String> nodeAttributeMap = getNodeAttributesMap(node.getChildNodes().item(i));
				if(nodeAttributeMap.values().contains(targetType)) {
					
					messageType = nodeAttributeMap.values().stream().filter(k -> !k.equals(targetType)).findAny().get();
					break;
				}
			}
		}
		if(!messageType.isBlank()) {
			Node childNode = getNodeForAttributeTypeAndValue(getParentNode(node), "name", messageType);
			
			result = getJsonSchemaForChildNode(childNode,result);
		}
		StringSubstitutor stringSubstitutor = new StringSubstitutor(Collections.singletonMap("root", result));
		jsonMessage = stringSubstitutor.replace(jsonMessage);
		
		return jsonMessage;
	}
	
	
	private static String getJsonSchemaForChildNode(Node childNode, String result) throws IOException {
		
		for(int i=0; i < childNode.getChildNodes().getLength(); i++) {
			
			if(childNode.getChildNodes().item(i).hasAttributes()) {
				
				Map<String, String> tmpMap = getNodeAttributesMap(childNode.getChildNodes().item(i));
				String targetType = tmpMap.get("type").contains(":") 
						? tmpMap.get("type").substring(tmpMap.get("type").indexOf(":")+1) : tmpMap.get("type");
				if(complexTypeMap.keySet().stream().anyMatch(k -> k.containsKey(targetType) )) {
					
					Node tmpNode = getNodeForAttributeTypeAndValue(getParentNode(childNode), "name", targetType);//childNode.getChildNodes().item(i)
					String jsonSchemaChildNode = new String(Files.readAllBytes(Paths.get(PATH + TEMPLATE_COMPLEXTYPE_OBJECT + EXT_JSON)));
					StringSubstitutor stringSubstitutor = new StringSubstitutor(Collections.singletonMap("title", tmpMap.get("name")));
					
					jsonSchemaChildNode = stringSubstitutor.replace(jsonSchemaChildNode);
					
					result = result.isBlank() ? getJsonSchemaForComplexType(tmpNode, jsonSchemaChildNode)
								: result + "," + getJsonSchemaForComplexType(tmpNode, jsonSchemaChildNode);
					System.out.print("%::");
				}
				
			} else if(childNode.getChildNodes().item(i).hasChildNodes()) {
				return getJsonSchemaForChildNode(childNode.getChildNodes().item(i), result);
			}
		}
		
		return result;
	}

	
	private static String getJsonSchemaForComplexType(Node node, String jsonSchemaChildNode) throws IOException {
		
		StringSubstitutor stringSubstitutor;
		List<String> attributeList = new ArrayList<>();
		
		for(int i=0; i < node.getChildNodes().getLength(); i++) {
			
			if(node.getChildNodes().item(i).hasAttributes()) {
				
				Map<String,String> tmpAttrMap = getNodeAttributesMap(node.getChildNodes().item(i));
				if(tmpAttrMap.containsKey("type")) {
					
					String attrType = tmpAttrMap.get("type").contains(":") 
							? tmpAttrMap.get("type").substring(tmpAttrMap.get("type").indexOf(":")+1) + "": tmpAttrMap.get("type");
					Node targetNode = getNodeForAttributeTypeAndValue(getParentNode(node), "name", attrType);
					stringSubstitutor = new StringSubstitutor(
							Collections.singletonMap("title", targetNode.getAttributes().getNamedItem("name").getNodeValue()));
					
					if(complexTypeMap.keySet().stream().anyMatch(k -> k.containsKey(attrType) )) {
						if(complexTypeMap.entrySet().stream()
								.anyMatch(k -> k.getKey().containsKey(attrType) && !k.getValue().isEmpty())) {
							
							String jsonComplexType = new String(Files.readAllBytes(Paths.get(PATH + TEMPLATE_COMPLEXTYPE_OBJECT + EXT_JSON)));
							if(tmpAttrMap.containsKey("maxOccurs")) {
						    	if(tmpAttrMap.get("maxOccurs").equals("unbounded")) {
						    		jsonComplexType = new String(Files.readAllBytes(Paths.get(PATH + TEMPLATE_COMPLEXTYPE_ARRAY + EXT_JSON)));
						    	}
						    }
							stringSubstitutor = new StringSubstitutor(Collections.singletonMap("title", tmpAttrMap.get("name")));
							jsonComplexType = stringSubstitutor.replace(jsonComplexType);
							
							attributeList.add(getJsonSchemaForComplexType(targetNode, jsonComplexType)); //Get schema for complex type
						} else {
							
							String jsonSimpleType = new String(Files.readAllBytes(Paths.get(PATH + TEMPLATE_SIMPLETYPE + EXT_JSON)));
							stringSubstitutor = new StringSubstitutor(Collections.singletonMap("title", tmpAttrMap.get("name")));
							jsonSimpleType = stringSubstitutor.replace(jsonSimpleType);
							
							stringSubstitutor = new StringSubstitutor(Collections.singletonMap("type", "string"));
							jsonSimpleType = stringSubstitutor.replace(jsonSimpleType);
							
							attributeList.add(jsonSimpleType);
						}
					
					} else if(simpleTypeMap.containsKey(attrType)) {
					    
						String jsonSimpleType = "";
						switch(simpleTypeMap.get(attrType)){
							case "string" , "number": 
								jsonSimpleType = new String(Files.readAllBytes(Paths.get(PATH + TEMPLATE_SIMPLETYPE + EXT_JSON)));
								break;
							case "dateTime" : 
								jsonSimpleType = new String(Files.readAllBytes(Paths.get(PATH + TEMPLATE_SIMPLETYPE_DATETIME + EXT_JSON)));
								break;
							default :
								jsonSimpleType = new String(Files.readAllBytes(Paths.get(PATH + TEMPLATE_SIMPLETYPE + EXT_JSON)));
						}
						if(tmpAttrMap.containsKey("maxOccurs")) {
					    	if(tmpAttrMap.get("maxOccurs").equals("unbounded")) {
					    		jsonSimpleType = new String(Files.readAllBytes(Paths.get(PATH + TEMPLATE_SIMPLETYPE_ARRAY + EXT_JSON)));
					    	}
					    	
					    }
						
						stringSubstitutor = new StringSubstitutor(Collections.singletonMap("title", tmpAttrMap.get("name")));
						jsonSimpleType = stringSubstitutor.replace(jsonSimpleType);
						
						stringSubstitutor = new StringSubstitutor(Collections.singletonMap("type", simpleTypeMap.get(attrType)));
						jsonSimpleType = stringSubstitutor.replace(jsonSimpleType);
						
						attributeList.add(jsonSimpleType);
					}
				}
			}
			else if(node.getChildNodes().item(i).hasChildNodes()) {
				return getJsonSchemaForComplexType(node.getChildNodes().item(i), jsonSchemaChildNode);
			}
		}
		
		Iterator<String> attributeListItr = attributeList.iterator();
		String collateAttrList = "";
		while(attributeListItr.hasNext()) {
			collateAttrList = collateAttrList.isBlank() ? attributeListItr.next() : collateAttrList + "," + attributeListItr.next();
		}
		stringSubstitutor = new StringSubstitutor(Collections.singletonMap("attribute", collateAttrList));
		jsonSchemaChildNode = stringSubstitutor.replace(jsonSchemaChildNode);
		
		return jsonSchemaChildNode;
		
	}
	
	
	/* node - Search in this Node by attribute type
	 */
	private static Node getNodeForAttributeTypeAndValue(Node node, String attrType, String value) {
		
		
		Node matchingNode = null ;
		
		for(int i = 0; i <  node.getChildNodes().getLength(); i++) {
			
			if(matchingNode != null) 
				break;
			if(node.getChildNodes().item(i).hasAttributes()) {
				
				Map<String, String> tmpMap = getNodeAttributesMap(node.getChildNodes().item(i));
				if(tmpMap.entrySet().stream().anyMatch(k -> k.getKey().equals(attrType) && k.getValue().equals(value))
						&& !node.getChildNodes().item(i).getNodeName().contains("xsd:element")){    /* remove elements */
					
					//match found
					matchingNode = node.getChildNodes().item(i);
					
				} else if(node.getChildNodes().item(i).hasChildNodes()) {
					matchingNode = getNodeForAttributeTypeAndValue(node.getChildNodes().item(i), attrType, value);
					//return getNodeForAttributeTypeAndValue(node.getChildNodes().item(i), attrType, value);
				}
				
			} else if(node.getChildNodes().item(i).hasChildNodes()) {
				matchingNode = getNodeForAttributeTypeAndValue(node.getChildNodes().item(i), attrType, value);
			}
			
		}
		
		// after search
		return matchingNode; // null if not found
		
	}


	/* Get parent node 'wsdl:types' */
	private static Node getParentNode(Node node) {
		
		if(node.getParentNode().getNodeName().contains("wsdl:types")) {
			return node.getParentNode();
		} 
		else {
			return getParentNode(node.getParentNode());
		}
	}
	

	private static Map<String, String> getNodeAttributesMap(Node node) {
		
		Map<String, String> attributeMap = new HashMap<>();
		
		if(node.hasAttributes()) {
			
			for(int i=0; i < node.getAttributes().getLength(); i++) {
				
				attributeMap.put(node.getAttributes().item(i).getNodeName(), node.getAttributes().item(i).getNodeValue());
				
			}
		}
		
		return attributeMap;
	}
	
	
	private static Node getRootNodeForNamespace(NodeList rootNodeList, String nameSpace) throws Exception{
		
		for(int i=0; i<rootNodeList.getLength(); i++) {
			
			if(rootNodeList.item(i).getAttributes() != null &&
					rootNodeList.item(i).getNodeName().equals("wsdl:types")) {
					
				for(int j=0 ; j <  rootNodeList.item(i).getChildNodes().getLength(); j++) {
					
					if(rootNodeList.item(i).getChildNodes().item(j).getAttributes() != null) {
						
						/* If namespace matches - return Node */
						for(int k=0; k < rootNodeList.item(i).getChildNodes().item(j).getAttributes().getLength() ; k++) {
							
							if(rootNodeList.item(i).getChildNodes().item(j).getAttributes().item(k).getNodeValue().equals(nameSpace)){
								
								return rootNodeList.item(i).getChildNodes().item(j);
							}
						}
					}
				}	
			}
		}
		
		throw new Exception("");
	}


	private static Map<String, String> getMessageNameTypeMap(NodeList rootNodeList){
		
		Map<String,String> messageNameElementMap = new HashMap<String,String>();
		
		for(int j = 0; j < rootNodeList.getLength(); j++) {
			
			if(rootNodeList.item(j).getAttributes() != null && 
				rootNodeList.item(j).getNodeName().contains("message")) {
					
				for(int k=0; k < rootNodeList.item(j).getChildNodes().getLength(); k++) {
					
					if(rootNodeList.item(j).getChildNodes().item(k).getAttributes() != null) {
						
						messageNameElementMap = getNameElementMap(rootNodeList.item(j).getChildNodes().item(k).getAttributes());
						
					}
				}
			}
		}
		
		return messageNameElementMap;
	}
	
	
	/* For fetching namespaces map */
	private static Map<String, String> getNameElementMap(NamedNodeMap namedNodeMap) {
		
		Map<String,String> nameTypeMap = new HashMap<String, String>();
		
		for(int i=0; i < namedNodeMap.getLength(); i++) {
					
			nameTypeMap.put(namedNodeMap.item(i).getNodeName(), namedNodeMap.item(i).getNodeValue());
			
		}
		
		return nameTypeMap;
	}
	
	/* node - Search in this Node by attribute name */
	/* private static Node getNodeForName(Node node, String name) {
		
		//Node tmpNode = node;
		
		if(node.hasChildNodes()) {
			
			for(int i = 0; i < node.getChildNodes().getLength(); i++) {
				
				if(node.getChildNodes().item(i).hasAttributes()) {
					
					Map<String, String> tmpMap = getNodeAttributesMap(node.getChildNodes().item(i));
					if(tmpMap.containsValue(name)) {
						
						String tmpKey = tmpMap.entrySet().stream().filter(e -> e.getValue().equals(name)).findAny().get().getKey();
						
						if(tmpKey.equals("name")) {
							
							return node.getChildNodes().item(i);
						} 
						else {
							getNodeForName(node.getChildNodes().item(i), name);
						}
					} 
					else {
						getNodeForName(node.getChildNodes().item(i), name);
					}
				}
			}
		} 
		
		return null;	// if no result 
	} */
	
	/* Substitute template by matching chars of map.key with template and replacing with map.value */
	/*private static String substituteString(String template, Map<String,String> map) {
		
		final Pattern p = Pattern.compile("\\$\\{(.+?)\\}");
		
		Matcher m = p.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String var = m.group(1);
            String replacement = map.get(var);
            m.appendReplacement(sb, replacement);
        }
        m.appendTail(sb);
        
        return sb.toString();
	}*/
	
	/*
	private static String getChildNodeJsonSchema(Node item) {
		
		Map<String, String> tmpMap = new HashMap<String, String>();
		for(int i=0; i < item.getChildNodes().getLength(); i++) {
				
			if(item.getChildNodes().item(i).hasAttributes()) {
				Map<String,String> attributeMap = getNodeAttributesMap(item.getChildNodes().item(i));
				Node node = getNodeForType(item, attributeMap.get("type"));
				
				tmpMap = getNodeAttributesMap(item.getChildNodes().item(i));
				
				System.out.println(tmpMap.toString());
			}
		}
		return null;
	}*/
	
	
	/******************************** Setting essential data ********************************************************************************
	 *  
	 *  namespaceMap - parent node (wsdl:definitions) namespace map
	 *  simpleTypeMap - map of (xsd:simpleType) name & type i.e., string, boolean, dateTime
	 * 	complexType - Key -> Map<k1, v1>: k1 = Complex type (String) v1 = Namespace (String), Val = List of Elements (String)
	 ***************************************************************************************************************************************/	
	
	private static Map<String,String> namespaceMap = new HashMap<>();
	private static Map<String,String> simpleTypeMap = new HashMap<>();
	private static HashMap<Map<String,String>, List<String>> complexTypeMap = new HashMap<>();
	
	
	private static boolean initialSetup(Document document) {
		
		NamedNodeMap nameSpaceAtributeMap = document.getDocumentElement().getAttributes();
		NodeList rootNodeList = document.getDocumentElement().getChildNodes();
		
		for(int i=1; i < nameSpaceAtributeMap.getLength(); i++) {
			
			namespaceMap.put(nameSpaceAtributeMap.item(i).getNodeName(), nameSpaceAtributeMap.item(i).getNodeValue());
			
		}
		System.out.println("namespaces setup successfull\n\t***");
		
		setupSimpleTypeMap(rootNodeList);
		
		System.out.println("simple types setup successfull\n\t***");
		
		setupComplexTypeMap(rootNodeList,new HashMap<String,String>());
			
		System.out.println("complex types setup successfull\n\t***");
		
		return !namespaceMap.isEmpty() && !simpleTypeMap.isEmpty() && !complexTypeMap.isEmpty();
	}
	

	private static void setupComplexTypeMap(NodeList nodeList, Map<String, String> localNameSpaceMap) {
		
		for(int i=0; i < nodeList.getLength(); i++) {
			
			if(nodeList.item(i).getNodeName().contains("xsd:schema") && nodeList.item(i).hasAttributes()) {
				
				for(int j=0; j < nodeList.item(i).getAttributes().getLength(); j++) {
					
					if(nodeList.item(i).getAttributes().getNamedItem("targetNamespace") != null &&
							nodeList.item(i).getAttributes().getNamedItem("targetNamespace").hasChildNodes()) {
						
						localNameSpaceMap.put(nodeList.item(i).getAttributes().item(j).getNodeName(), 
											nodeList.item(i).getAttributes().item(j).getNodeValue());
					}	
				}
			}
			if(nodeList.item(i).hasChildNodes() && nodeList.item(i).getNodeName().contains("xsd:complexType")) {

				String complexTypeKey = nodeList.item(i).getAttributes().item(0).getNodeValue();
				List<String> tmpList = getComplexTypeElementsList(nodeList.item(i), new ArrayList<>(), localNameSpaceMap);
				Map<String,String> complexTypeNamespaceMap = Collections.singletonMap(complexTypeKey, localNameSpaceMap.get("targetNamespace"));
				
				complexTypeMap.put(complexTypeNamespaceMap, tmpList);
				
			} else if(nodeList.item(i).hasChildNodes()) {
					setupComplexTypeMap(nodeList.item(i).getChildNodes(),localNameSpaceMap);
			}
		}
	}
	

	private static List<String> getComplexTypeElementsList(Node item, List<String> elementsList, Map<String, String> localNameSpaceMap) {
		
		for(int i=0; i < item.getChildNodes().getLength(); i++) {
			
			if(item.getChildNodes().item(i).hasAttributes()) {
				
				/* Get all attributes('name') from element tag for any given complex type */
				if(item.getChildNodes().item(i).getNodeName().contains("xsd:element") ||
						item.getChildNodes().item(i).getNodeName().contains("xsd:simpleContent")) {
					
					Map<String,String> tmpMap = getNodeAttributesMap(item.getChildNodes().item(i));
					if(tmpMap.containsKey("name") || tmpMap.containsKey("base")) {
						elementsList.add(tmpMap.entrySet().stream().filter(k->
							k.getKey().contains("name") || k.getKey().contains("base")
						).findAny().get().getValue());
					}
					
				} else if(item.hasChildNodes()) {
					getComplexTypeElementsList(item.getChildNodes().item(i), elementsList, localNameSpaceMap);
					
				}
			} else if(item.hasChildNodes()) {
				getComplexTypeElementsList(item.getChildNodes().item(i), elementsList, localNameSpaceMap);
				
			}
		}
		
		return elementsList;
	}
	

	private static void setupSimpleTypeMap(NodeList nodeList) {
		
		for(int i=0; i < nodeList.getLength(); i++) {
			
			if(nodeList.item(i).hasAttributes() && nodeList.item(i).getNodeName().contains("restriction")) {
				
				Node parentNode = nodeList.item(i).getParentNode().hasAttributes() ? nodeList.item(i).getParentNode()
											: nodeList.item(i).getParentNode().getParentNode();
				
				switch(nodeList.item(i).getAttributes().item(0).getNodeValue()) {
					case "xsd:token", "xsd:string":
						simpleTypeMap.put(parentNode.getAttributes().item(0).getNodeValue(), "string");
						break;
					case "xsd:date":
						simpleTypeMap.put(parentNode.getAttributes().item(0).getNodeValue(), "string");
						break;
					case "xsd:dateTime":
						simpleTypeMap.put(parentNode.getAttributes().item(0).getNodeValue(), "dateTime");
						break;
					case "xsd:boolean":
						simpleTypeMap.put(parentNode.getAttributes().item(0).getNodeValue(), "boolean");
						break;
					case "xsd:decimal":
						simpleTypeMap.put(parentNode.getAttributes().item(0).getNodeValue(), "number");
						break;
					default:
						simpleTypeMap.put(parentNode.getAttributes().item(0).getNodeValue(), "string");
				}
				
			} else {
				if(nodeList.item(i).hasChildNodes()) {
					setupSimpleTypeMap(nodeList.item(i).getChildNodes());
				}
			}
		}
	}
	/****************************************************************************************************************************************
	 */	
	

	private static DocumentBuilder createDocumentBuilder() throws ParserConfigurationException {
		
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		
		return factory.newDocumentBuilder();
	}
	

	/* Prints input wsdl file into console and in '<fileName>-verify.wsdl' */
	private static void verifyDocument(Document doc, OutputStream out) throws IOException, TransformerException {
		
	    TransformerFactory tf = TransformerFactory.newInstance();
	    tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
	    tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
	    
	    Transformer transformer = tf.newTransformer();
	    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
	    transformer.setOutputProperty(OutputKeys.METHOD, "xml");
	    //transformer.setOutputProperty(OutputKeys.INDENT, "yes");
	    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
	    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
	    
	    //transformer.transform(new DOMSource(doc),new StreamResult(new OutputStreamWriter(out, "UTF-8"))); //Print on Console
	    
	    transformer.transform(new DOMSource(doc), 
		         new StreamResult(new FileWriter(new File( PATH + INPUT_FILE + VERIFY + EXT_WSDL )))); //Print output in file
	}
	

}
