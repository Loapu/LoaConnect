package dev.loapu.loaconnect.paper.storage;

import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UserStorage
{
	private static final Map<UUID, UserStorage> users = new ConcurrentHashMap<>();
	

	private OIDCTokens tokens;
	private final Nonce nonce;
	
	private UserStorage(OIDCTokens tokens, Nonce nonce)
	{
		this.tokens = tokens;
		this.nonce = nonce;
	}
	
	public static UserStorage add(UUID uniqueId, OIDCTokens tokens, Nonce nonce)
	{
		UserStorage user = new UserStorage(tokens, nonce);
		users.put(uniqueId, user);
		return user;
	}
	
	public void updateTokens(OIDCTokens newTokens)
	{
		tokens = newTokens;
	}
	
	public OIDCTokens tokens()
	{
		return tokens;
	}
	
	public Nonce nonce()
	{
		return nonce;
	}
	
	public static UserStorage get(UUID uniqueId)
	{
		return users.get(uniqueId);
	}
	
	public static void removeIfEmpty(UUID uniqueId)
	{
		UserStorage user = users.get(uniqueId);
		if (user != null && user.tokens == null) users.remove(uniqueId);
	}
}
