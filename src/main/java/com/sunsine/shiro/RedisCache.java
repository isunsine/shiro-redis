package com.sunsine.shiro;
import org.apache.shiro.cache.Cache;
import org.apache.shiro.cache.CacheException;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
public class RedisCache<K, V> implements Cache<K, V> {
	private static Logger logger = LoggerFactory.getLogger(RedisCache.class);
	private RedisSerializer<String> keySerializer = new StringSerializer();
	private RedisSerializer<Object> valueSerializer = new ObjectSerializer();
	private IRedisManager redisManager;
	private String keyPrefix = "";
	private int expire = 0;
	/**
	 * Construction
	 * @param redisManager
	 */
	public RedisCache(IRedisManager redisManager, RedisSerializer<String> keySerializer,
			RedisSerializer<Object> valueSerializer, String prefix, int expire) {
		if (redisManager == null) {
			throw new IllegalArgumentException("redisManager cannot be null.");
		}
		this.redisManager = redisManager;
		if (keySerializer == null) {
			throw new IllegalArgumentException("keySerializer cannot be null.");
		}
		this.keySerializer = keySerializer;
		if (valueSerializer == null) {
			throw new IllegalArgumentException("valueSerializer cannot be null.");
		}
		this.valueSerializer = valueSerializer;
		if (prefix != null && !"".equals(prefix)) {
			this.keyPrefix = prefix;
		}
		if (expire != -1) {
			this.expire = expire;
		}
	}
	@SuppressWarnings("unchecked")
	@Override
	public V get(K key)
		throws CacheException {
		logger.debug("get key [" + key + "]");
		if (key == null) {
			return null;
		}
		try {
			Object redisCacheKey = getRedisCacheKey(key);
			byte[] rawValue = redisManager.get(keySerializer.serialize(redisCacheKey.toString()));
			if (rawValue == null) {
				return null;
			}
			V value = (V)valueSerializer.deserialize(rawValue);
			return value;
		} catch (SerializationException e) {
			throw new CacheException(e);
		}
	}
	@Override
	public V put(K key, V value)
		throws CacheException {
		logger.debug("put key [" + key + "]");
		if (key == null) {
			logger.warn("Saving a null key is meaningless, return value directly without call Redis.");
			return value;
		}
		try {
			Object redisCacheKey = getRedisCacheKey(key);
			redisManager.set(keySerializer.serialize(redisCacheKey.toString()),
				value != null ? valueSerializer.serialize(value) : null, expire);
			return value;
		} catch (SerializationException e) {
			throw new CacheException(e);
		}
	}
	@SuppressWarnings("unchecked")
	@Override
	public V remove(K key)
		throws CacheException {
		logger.debug("remove key [" + key + "]");
		if (key == null) {
			return null;
		}
		try {
			Object redisCacheKey = getRedisCacheKey(key);
			byte[] rawValue = redisManager.get(keySerializer.serialize(redisCacheKey.toString()));
			V previous = (V)valueSerializer.deserialize(rawValue);
			redisManager.del(keySerializer.serialize(redisCacheKey.toString()));
			return previous;
		} catch (SerializationException e) {
			throw new CacheException(e);
		}
	}
	private Object getRedisCacheKey(K key) {
		if (key == null) {
			return null;
		}
		Object redisKey = key;
		if (keySerializer instanceof StringSerializer) {
			redisKey = getStringRedisKey(key);
		}
		if (redisKey instanceof String) {
			return this.keyPrefix + (String)redisKey;
		}
		return redisKey;
	}
	private Object getStringRedisKey(K key) {
		Object redisKey;
		if (key instanceof PrincipalCollection) {
			redisKey = getRedisKeyFromPrincipalCollection((PrincipalCollection)key);
		} else {
			redisKey = key.toString();
		}
		return redisKey;
	}
	private Object getRedisKeyFromPrincipalCollection(PrincipalCollection key) {
		Object redisKey;
		List<String> realmNames = getRealmNames(key);
		Collections.sort(realmNames);
		redisKey = joinRealmNames(realmNames);
		return redisKey;
	}
	private List<String> getRealmNames(PrincipalCollection key) {
		List<String> realmArr = new ArrayList<String>();
		Set<String> realmNames = key.getRealmNames();
		for (String realmName : realmNames) {
			realmArr.add(realmName);
		}
		return realmArr;
	}
	private Object joinRealmNames(List<String> realmArr) {
		Object redisKey;
		StringBuilder redisKeyBuilder = new StringBuilder();
		for (int i = 0; i < realmArr.size(); i++) {
			String s = realmArr.get(i);
			redisKeyBuilder.append(s);
		}
		redisKey = redisKeyBuilder.toString();
		return redisKey;
	}
	@Override
	public void clear()
		throws CacheException {
		logger.debug("clear cache");
		Set<byte[]> keys = null;
		try {
			keys = redisManager.keys(keySerializer.serialize(this.keyPrefix + "*"));
		} catch (SerializationException e) {
			logger.error("get keys error", e);
		}
		if (keys == null || keys.size() == 0) {
			return;
		}
		for (byte[] key : keys) {
			redisManager.del(key);
		}
	}
	@Override
	public int size() {
		Long longSize = new Long(redisManager.dbSize());
		return longSize.intValue();
	}
	@SuppressWarnings("unchecked")
	@Override
	public Set<K> keys() {
		Set<byte[]> keys = null;
		try {
			keys = redisManager.keys(keySerializer.serialize(this.keyPrefix + "*"));
		} catch (SerializationException e) {
			logger.error("get keys error", e);
			return Collections.emptySet();
		}
		if (CollectionUtils.isEmpty(keys)) {
			return Collections.emptySet();
		}
		Set<K> convertedKeys = new HashSet<K>();
		for (byte[] key : keys) {
			try {
				convertedKeys.add((K)keySerializer.deserialize(key));
			} catch (SerializationException e) {
				logger.error("deserialize keys error", e);
			}
		}
		return convertedKeys;
	}
	@SuppressWarnings("unchecked")
	@Override
	public Collection<V> values() {
		Set<byte[]> keys = null;
		try {
			keys = redisManager.keys(keySerializer.serialize(this.keyPrefix + "*"));
		} catch (SerializationException e) {
			logger.error("get values error", e);
			return Collections.emptySet();
		}
		if (CollectionUtils.isEmpty(keys)) {
			return Collections.emptySet();
		}
		List<V> values = new ArrayList<V>(keys.size());
		for (byte[] key : keys) {
			V value = null;
			try {
				value = (V)valueSerializer.deserialize(redisManager.get(key));
			} catch (SerializationException e) {
				logger.error("deserialize values= error", e);
			}
			if (value != null) {
				values.add(value);
			}
		}
		return Collections.unmodifiableList(values);
	}
	public String getKeyPrefix() {
		return keyPrefix;
	}
	public void setKeyPrefix(String keyPrefix) {
		this.keyPrefix = keyPrefix;
	}
}