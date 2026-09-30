package dev.agentstudio.runtime;

import java.util.*;

/** Produces bounded, credential-safe diagnostic copies for persisted run traces. */
final class RunTraceSanitizer {
    private RunTraceSanitizer(){}
    static Map<String,Object> map(Map<String,Object> value){Object safe=value(value,0);return safe instanceof Map<?,?> result?cast(result):Map.of();}
    static Object value(Object value,int depth){
        if(depth>7)return "[depth limited]";
        if(value instanceof Map<?,?> source){Map<String,Object> result=new LinkedHashMap<>();int count=0;for(var entry:source.entrySet()){if(count++>=100)break;String key=String.valueOf(entry.getKey());result.put(key,sensitive(key)?"••••••••":value(entry.getValue(),depth+1));}return Collections.unmodifiableMap(result);}
        if(value instanceof Collection<?> source)return source.stream().limit(50).map(item->value(item,depth+1)).toList();
        if(value instanceof String text)return text.length()>12_000?text.substring(0,12_000)+"… [truncated]":text;
        return value;
    }
    private static boolean sensitive(String key){return key.toLowerCase(Locale.ROOT).matches(".*(password|secret|credential|authorization|api.?key|access.?token|refresh.?token|base64|binary|file.?bytes|document.?bytes).*");}
    @SuppressWarnings("unchecked") private static Map<String,Object> cast(Map<?,?> value){return (Map<String,Object>)value;}
}
