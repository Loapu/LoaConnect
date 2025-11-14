package dev.loapu.loaconnect.paper.storage;

import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import dev.loapu.loaconnect.paper.LoaConnectPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class UserStorage
{
	private static final Map<UUID, UserStorage> USERS_BY_UUID = new ConcurrentHashMap<>();
	private static final Map<State, UserStorage> USERS_BY_STATE = new ConcurrentHashMap<>();
	
	private final UUID uniqueId;
	private OIDCTokens tokens;
	private State state;
	private Nonce nonce;
	
	private UserStorage(UUID uniqueId, OIDCTokens tokens, State state, Nonce nonce)
	{
		this.uniqueId = uniqueId;
		this.state = state;
		this.tokens = tokens;
		this.nonce = nonce;
	}
	
	public static UserStorage add(UUID uniqueId, State state, Nonce nonce)
	{
		UserStorage user = new UserStorage(uniqueId, null, state, nonce);
		USERS_BY_UUID.put(uniqueId, user);
		if (state != null) USERS_BY_STATE.put(state, user);
		return user;
	}
	
	public void updateTokens(OIDCTokens newTokens)
	{
		tokens = newTokens;
	}
	
	public UUID uniqueId()
	{
		return uniqueId;
	}
	
	public OIDCTokens tokens()
	{
		return tokens;
	}
	
	public State state()
	{
		return state;
	}
	
	public void flushState()
	{
		if (state == null) return;
		USERS_BY_STATE.remove(state);
		state = null;
	}
	
	public Nonce nonce()
	{
		return nonce;
	}
	
	public void flushNonce()
	{
		nonce = null;
	}
	
	public static UserStorage get(UUID uniqueId)
	{
		return USERS_BY_UUID.get(uniqueId);
	}
	
	public static void get(UUID uniqueId, CompletableFuture<UserStorage> userStorageCompletableFuture)
	{
		LoaConnectPlugin plugin = LoaConnectPlugin.instance();
		plugin.getServer().getAsyncScheduler().runNow(plugin, task ->
		{
			UserStorage user = get(uniqueId);
			if (user == null)
			{
				user = StorageProvider.instance().fetchUser(uniqueId);
			}
			userStorageCompletableFuture.complete(user);
		});
	}
	
	public static UserStorage get(State state)
	{
		return USERS_BY_STATE.get(state);
	}
	
	public static void removeUserIfNoTokens(UUID uniqueId)
	{
		UserStorage user = USERS_BY_UUID.get(uniqueId);
		if (user != null && user.tokens == null) USERS_BY_UUID.remove(uniqueId);
	}
	
	public static void save()
	{
		USERS_BY_UUID.forEach((s, u) -> StorageProvider.instance().saveUser(u));
	}
}
