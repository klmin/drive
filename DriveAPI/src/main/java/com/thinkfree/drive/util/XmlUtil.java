package com.thinkfree.drive.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class XmlUtil {
    
    private static final XmlMapper xmlMapper;
    
    static{
        xmlMapper = new XmlMapper();
        xmlMapper.enable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION);
    }
    
    public static String writeValueAsString(Object obj){
        try{
            return xmlMapper.writeValueAsString(obj);
        }catch(JsonProcessingException e){
            throw new RuntimeException("Failed to convert object to XML", e);
        }
    }
    
    public static <T> T readValue(String value, Class<T> valueType) {
        try {
            return xmlMapper.readValue(value, valueType);
        }catch (JsonProcessingException e){
            throw new RuntimeException("Failed to read object to XML", e);
        }
    }
}
