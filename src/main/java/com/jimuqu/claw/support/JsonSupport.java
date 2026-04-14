package com.jimuqu.claw.support;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ReflectUtil;
import org.noear.snack4.ONode;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.Calendar;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class JsonSupport {
    private JsonSupport() {
    }

    public static String toJson(Object value) {
        return ONode.serialize(normalize(value, new IdentityHashMap<Object, Boolean>()));
    }

    public static <T> T fromJson(String json, Class<T> type) {
        Object raw = ONode.deserialize(json, Object.class);
        if (raw == null || type == null) {
            return null;
        }

        if (Object.class == type) {
            return type.cast(raw);
        }

        return type.cast(convertValue(raw, type, type));
    }

    private static Object convertValue(Object raw, Class<?> type, Type genericType) {
        if (raw == null || type == null) {
            return null;
        }

        if (Object.class == type) {
            return raw;
        }

        if (type.isInstance(raw)
                && !List.class.isAssignableFrom(type)
                && !Map.class.isAssignableFrom(type)) {
            return raw;
        }

        if (String.class == type
                || Number.class.isAssignableFrom(type)
                || Boolean.class == type
                || Character.class == type
                || type.isPrimitive()) {
            return Convert.convert(type, raw);
        }

        if (Instant.class == type) {
            return convertInstant(raw);
        }

        if (Date.class == type) {
            Instant instant = convertInstant(raw);
            return instant == null ? null : Date.from(instant);
        }

        if (Calendar.class.isAssignableFrom(type)) {
            Instant instant = convertInstant(raw);
            if (instant == null) {
                return null;
            }
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(Date.from(instant));
            return calendar;
        }

        if (type.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), String.valueOf(raw));
        }

        if (type.isArray()) {
            return convertArray(raw, type.getComponentType(), genericComponentType(genericType));
        }

        if (raw instanceof List && List.class.isAssignableFrom(type)) {
            return convertList((List<?>) raw, genericType);
        }

        if (raw instanceof Map && Map.class.isAssignableFrom(type)) {
            return convertMap((Map<?, ?>) raw, genericType);
        }

        if (raw instanceof Map) {
            return convertBean((Map<?, ?>) raw, type);
        }

        return Convert.convert(type, raw);
    }

    private static Object convertArray(Object raw, Class<?> componentType, Type componentGenericType) {
        if (!(raw instanceof List)) {
            return null;
        }

        List<?> source = (List<?>) raw;
        Object array = Array.newInstance(componentType, source.size());
        for (int i = 0; i < source.size(); i++) {
            Array.set(array, i, convertValue(source.get(i), componentType, componentGenericType));
        }
        return array;
    }

    private static List<Object> convertList(List<?> source, Type genericType) {
        Type itemType = listItemType(genericType);
        Class<?> itemClass = rawClass(itemType);
        List<Object> target = new ArrayList<Object>(source.size());
        for (Object item : source) {
            target.add(convertValue(item, itemClass, itemType));
        }
        return target;
    }

    private static Map<String, Object> convertMap(Map<?, ?> source, Type genericType) {
        Type valueType = mapValueType(genericType);
        Class<?> valueClass = rawClass(valueType);
        Map<String, Object> target = new LinkedHashMap<String, Object>(source.size());
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            target.put(String.valueOf(entry.getKey()), convertValue(entry.getValue(), valueClass, valueType));
        }
        return target;
    }

    private static Object convertBean(Map<?, ?> source, Class<?> type) {
        Object bean = ReflectUtil.newInstanceIfPossible(type);
        if (bean == null) {
            return Convert.convert(type, source);
        }

        for (Field field : ReflectUtil.getFields(type)) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            String name = field.getName();
            if (!source.containsKey(name)) {
                continue;
            }

            Object converted = convertValue(source.get(name), field.getType(), field.getGenericType());
            if (converted == null && field.getType().isPrimitive()) {
                continue;
            }

            ReflectUtil.setFieldValue(bean, field, converted);
        }
        return bean;
    }

    private static Instant convertInstant(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Instant) {
            return (Instant) raw;
        }
        if (raw instanceof Number) {
            return Instant.ofEpochMilli(((Number) raw).longValue());
        }

        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        return Instant.parse(text);
    }

    private static Type listItemType(Type genericType) {
        if (genericType instanceof ParameterizedType) {
            Type[] arguments = ((ParameterizedType) genericType).getActualTypeArguments();
            if (arguments.length > 0) {
                return arguments[0];
            }
        }
        return Object.class;
    }

    private static Type mapValueType(Type genericType) {
        if (genericType instanceof ParameterizedType) {
            Type[] arguments = ((ParameterizedType) genericType).getActualTypeArguments();
            if (arguments.length > 1) {
                return arguments[1];
            }
        }
        return Object.class;
    }

    private static Type genericComponentType(Type genericType) {
        if (genericType instanceof GenericArrayType) {
            return ((GenericArrayType) genericType).getGenericComponentType();
        }
        if (genericType instanceof Class && ((Class<?>) genericType).isArray()) {
            return ((Class<?>) genericType).getComponentType();
        }
        return Object.class;
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            Type raw = ((ParameterizedType) type).getRawType();
            if (raw instanceof Class) {
                return (Class<?>) raw;
            }
        }
        if (type instanceof GenericArrayType) {
            Class<?> component = rawClass(((GenericArrayType) type).getGenericComponentType());
            return Array.newInstance(component, 0).getClass();
        }
        return Object.class;
    }

    private static Object normalize(Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean) {
            return value;
        }

        if (value instanceof Character) {
            return value.toString();
        }

        if (value instanceof Enum) {
            return ((Enum<?>) value).name();
        }

        if (value instanceof Optional) {
            Optional<?> optional = (Optional<?>) value;
            return optional.isPresent() ? normalize(optional.get(), visiting) : null;
        }

        if (value instanceof TemporalAccessor
                || value instanceof UUID
                || value instanceof URI
                || value instanceof URL
                || value instanceof File
                || value instanceof Path
                || value instanceof Class) {
            return String.valueOf(value);
        }

        if (value instanceof Date) {
            return ((Date) value).toInstant().toString();
        }

        if (value instanceof Calendar) {
            return ((Calendar) value).toInstant().toString();
        }

        if (value instanceof Map) {
            Map<?, ?> source = (Map<?, ?>) value;
            Map<String, Object> target = new LinkedHashMap<String, Object>(source.size());
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                target.put(String.valueOf(entry.getKey()), normalize(entry.getValue(), visiting));
            }
            return target;
        }

        if (value instanceof Iterable) {
            List<Object> target = new ArrayList<Object>();
            for (Object item : (Iterable<?>) value) {
                target.add(normalize(item, visiting));
            }
            return target;
        }

        Class<?> type = value.getClass();
        if (type.isArray()) {
            int length = Array.getLength(value);
            List<Object> target = new ArrayList<Object>(length);
            for (int i = 0; i < length; i++) {
                target.add(normalize(Array.get(value, i), visiting));
            }
            return target;
        }

        String packageName = packageNameOf(type);
        if (packageName.startsWith("java.") || !BeanUtil.isReadableBean(type)) {
            return String.valueOf(value);
        }

        if (visiting.containsKey(value)) {
            throw new IllegalArgumentException("Circular reference is not supported for JSON serialization: " + type.getName());
        }

        visiting.put(value, Boolean.TRUE);
        try {
            Map<String, Object> beanMap = BeanUtil.beanToMap(value, new LinkedHashMap<String, Object>(), false, false);
            Map<String, Object> normalized = new LinkedHashMap<String, Object>(beanMap.size());
            for (Map.Entry<String, Object> entry : beanMap.entrySet()) {
                normalized.put(entry.getKey(), normalize(entry.getValue(), visiting));
            }
            return normalized;
        } finally {
            visiting.remove(value);
        }
    }

    private static String packageNameOf(Class<?> type) {
        Package pkg = type.getPackage();
        return pkg == null ? "" : pkg.getName();
    }
}
