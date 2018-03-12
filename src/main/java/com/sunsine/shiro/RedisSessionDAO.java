package com.sunsine.shiro;
import org.apache.shiro.session.Session;
import org.apache.shiro.session.UnknownSessionException;
import org.apache.shiro.session.mgt.eis.AbstractSessionDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
public class RedisSessionDAO extends AbstractSessionDAO implements SessionDAO {
	private static Logger logger = LoggerFactory.getLogger(RedisSessionDAO.class);
	private static final String DEFAULT_SESSION_KEY_PREFIX = "shiro:session:";
	private String keyPrefix = DEFAULT_SESSION_KEY_PREFIX;
	private static final long DEFAULT_SESSION_IN_MEMORY_TIMEOUT = 1000L;
	/**
	 * doReadSession be called about 10 times when login. Save Session in ThreadLocal to resolve
	 * this problem. sessionInMemoryTimeout is expiration of Session in ThreadLocal. The default
	 * value is 1000 milliseconds (1s). Most of time, you don't need to change it.
	 */
	private long sessionInMemoryTimeout = DEFAULT_SESSION_IN_MEMORY_TIMEOUT;
	// expire time in seconds
	private static final int DEFAULT_EXPIRE = 1800;
	/**
	 * Please make sure expire is longer than sesion.getTimeout()
	 */
	private int expire = DEFAULT_EXPIRE;
	private IRedisManager redisManager;
	private RedisSerializer<String> keySerializer = new StringSerializer();
	private RedisSerializer<Object> valueSerializer = new ObjectSerializer();
	private static ThreadLocal<Map<Serializable, SessionInMemory>> sessionsInThread = new ThreadLocal<Map<Serializable, SessionInMemory>>();
	@Override
	public void update(Session session)
		throws UnknownSessionException {
		this.saveSession(session);
	}
	/**
	 * save session
	 * @param session
	 * @throws UnknownSessionException
	 */
	private void saveSession(Session session)
		throws UnknownSessionException {
		if (session == null || session.getId() == null) {
			logger.error("session or session id is null");
			throw new UnknownSessionException("session or session id is null");
		}
		byte[] key;
		byte[] value;
		try {
			key = keySerializer.serialize(getRedisSessionKey(session.getId()));
			value = valueSerializer.serialize(session);
		} catch (SerializationException e) {
			logger.error("serialize session error. session id=" + session.getId());
			throw new UnknownSessionException(e);
		}
		if (expire * 1000 < session.getTimeout()) {
			logger.warn("Redis session expire time: "
				+ (expire * 1000)
				+ " is less than Session timeout: "
				+ session.getTimeout()
				+ " . It may cause some problems.");
		}
		this.redisManager.set(key, value, expire);
	}
	@Override
	public void delete(Session session) {
		if (session == null || session.getId() == null) {
			logger.error("session or session id is null");
			return;
		}
		try {
			redisManager.del(keySerializer.serialize(getRedisSessionKey(session.getId())));
		} catch (SerializationException e) {
			logger.error("delete session error. session id=" + session.getId());
		}
	}
	@Override
	public Collection<Session> getActiveSessions() {
		Set<Session> sessions = new HashSet<Session>();
		try {
			Set<byte[]> keys = redisManager.keys(this.keySerializer.serialize(this.keyPrefix + "*"));
			if (keys != null && keys.size() > 0) {
				for (byte[] key : keys) {
					Session s = (Session)valueSerializer.deserialize(redisManager.get(key));
					sessions.add(s);
				}
			}
		} catch (SerializationException e) {
			logger.error("get active sessions error.");
		}
		return sessions;
	}
	@Override
	protected Serializable doCreate(Session session) {
		if (session == null) {
			logger.error("session is null");
			throw new UnknownSessionException("session is null");
		}
		Serializable sessionId = this.generateSessionId(session);
		this.assignSessionId(session, sessionId);
		this.saveSession(session);
		return sessionId;
	}
	@Override
	protected Session doReadSession(Serializable sessionId) {
		if (sessionId == null) {
			logger.warn("session id is null");
			return null;
		}
		Session s = getSessionFromThreadLocal(sessionId);
		if (s != null) {
			return s;
		}
		logger.debug("read session from redis");
		try {
			s =
				(Session)valueSerializer
						.deserialize(redisManager.get(keySerializer.serialize(getRedisSessionKey(sessionId))));
			setSessionToThreadLocal(sessionId, s);
		} catch (SerializationException e) {
			logger.error("read session error. settionId=" + sessionId);
		}
		return s;
	}
	private void setSessionToThreadLocal(Serializable sessionId, Session s) {
		Map<Serializable, SessionInMemory> sessionMap = (Map<Serializable, SessionInMemory>)sessionsInThread.get();
		if (sessionMap == null) {
			sessionMap = new HashMap<Serializable, SessionInMemory>();
			sessionsInThread.set(sessionMap);
		}
		SessionInMemory sessionInMemory = new SessionInMemory();
		sessionInMemory.setCreateTime(new Date());
		sessionInMemory.setSession(s);
		sessionMap.put(sessionId, sessionInMemory);
	}
	private Session getSessionFromThreadLocal(Serializable sessionId) {
		Session s = null;
		if (sessionsInThread.get() == null) {
			return null;
		}
		Map<Serializable, SessionInMemory> sessionMap = (Map<Serializable, SessionInMemory>)sessionsInThread.get();
		SessionInMemory sessionInMemory = sessionMap.get(sessionId);
		if (sessionInMemory == null) {
			return null;
		}
		Date now = new Date();
		long duration = now.getTime() - sessionInMemory.getCreateTime().getTime();
		if (duration < sessionInMemoryTimeout) {
			s = sessionInMemory.getSession();
			logger.debug("read session from memory");
		} else {
			sessionMap.remove(sessionId);
		}
		return s;
	}
	private String getRedisSessionKey(Serializable sessionId) {
		return this.keyPrefix + sessionId;
	}
	public IRedisManager getRedisManager() {
		return redisManager;
	}
	public void setRedisManager(IRedisManager redisManager) {
		this.redisManager = redisManager;
	}
	public String getKeyPrefix() {
		return keyPrefix;
	}
	public void setKeyPrefix(String keyPrefix) {
		this.keyPrefix = keyPrefix;
	}
	public RedisSerializer<String> getKeySerializer() {
		return keySerializer;
	}
	public void setKeySerializer(RedisSerializer<String> keySerializer) {
		this.keySerializer = keySerializer;
	}
	public RedisSerializer<Object> getValueSerializer() {
		return valueSerializer;
	}
	public void setValueSerializer(RedisSerializer<Object> valueSerializer) {
		this.valueSerializer = valueSerializer;
	}
	public long getSessionInMemoryTimeout() {
		return sessionInMemoryTimeout;
	}
	public void setSessionInMemoryTimeout(long sessionInMemoryTimeout) {
		this.sessionInMemoryTimeout = sessionInMemoryTimeout;
	}
	public int getExpire() {
		return expire;
	}
	public void setExpire(int expire) {
		this.expire = expire;
	}
	@Override
	public Collection<Session> getActiveSessions(boolean includeLeave) {
		return getActiveSessions(true);
	}
	@Override
	public Collection<Session> getActiveSessions(boolean includeLeave, Object principal, Session filterSession) {
		return getActiveSessions(includeLeave, null, null);
	}
}
