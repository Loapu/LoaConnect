package dev.loapu.loaconnect.paper.listeners;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import dev.loapu.loaconnect.paper.LoaConnectPlugin;
import dev.loapu.loaconnect.paper.authentication.TokenUtil;
import dev.loapu.loaconnect.paper.hooks.LuckPermsHook;
import dev.loapu.loaconnect.paper.storage.UserStorage;
import dev.loapu.loaconnect.paper.transactions.Transaction;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ConnectionListener implements Listener
{
	private final LoaConnectPlugin plugin;
	private final Map<UUID, CompletableFuture<Boolean>> loginRequests = new ConcurrentHashMap<>();
	
	public ConnectionListener(LoaConnectPlugin plugin)
	{
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
		this.plugin = plugin;
	}
	
	@SuppressWarnings("UnstableApiUsage")
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerConfigure(AsyncPlayerConnectionConfigureEvent event)
	{
		Transaction transaction = new Transaction();
		PlayerConfigurationConnection connection = event.getConnection();
		PlayerProfile profile = connection.getProfile();
		UUID uuidOfConnectingPlayer = profile.getId();
		
		if (uuidOfConnectingPlayer == null)
		{
			transaction.error("Player not found.");
			connection.disconnect(Component.text("There was an error getting your UUID.", NamedTextColor.RED)); //TODO: Make this configurable
			transaction.end();
			return;
		}
		
		if (plugin.providerMetadata() == null)
		{
			transaction.error("Provider not found.");
			connection.disconnect(Component.text("Provider not found.", NamedTextColor.RED));
			transaction.end();
			return;
		}
		
		String nameOfConnectingPlayer = profile.getName();
		
		nameOfConnectingPlayer = nameOfConnectingPlayer == null ? "Unknown" : nameOfConnectingPlayer;
		
		transaction.info("{} ({}) tries to connect...", nameOfConnectingPlayer, uuidOfConnectingPlayer);
		
		CompletableFuture<Boolean> loginRequest = new CompletableFuture<>();
		loginRequest.completeOnTimeout(false, plugin.configProvider().general().loginTimeoutInMinutes(), TimeUnit.MINUTES);
		
		loginRequests.put(uuidOfConnectingPlayer, loginRequest);
		CompletableFuture<UserStorage> userStorage = new CompletableFuture<>();
		userStorage.completeOnTimeout(null, 10, TimeUnit.SECONDS);
		UserStorage.get(uuidOfConnectingPlayer,  userStorage);
		UserStorage user = userStorage.join();
		Audience audience = connection.getAudience();
		
		if (user == null)
		{
			transaction.info("{} has not been authenticated before.", nameOfConnectingPlayer);
			audience.showDialog(plugin.createDialog(uuidOfConnectingPlayer, transaction));
		}
		else
		{
			transaction.info("{} has been authenticated before.", nameOfConnectingPlayer);
			authenticateKnownUser(user, audience, transaction);
		}
		
		boolean isPlayerAllowedToJoin = loginRequest.join();
		
		if (!isPlayerAllowedToJoin)
		{
			transaction.warn("{} is not allowed to join.", nameOfConnectingPlayer);
			audience.closeDialog();
			connection.disconnect(Component.text("Deine Verbindung zum Server wurde getrennt.", NamedTextColor.RED));
		}
		else
		{
			plugin.getServer().getAsyncScheduler().runDelayed(plugin, task ->
			{
				LuckPermsHook.instance().syncGroups(UserStorage.get(uuidOfConnectingPlayer));
			}, 1L, TimeUnit.SECONDS);
		}
		
		loginRequests.remove(uuidOfConnectingPlayer);
		transaction.end();
	}
	
	@EventHandler
	void onConnectionClose(PlayerConnectionCloseEvent event)
	{
		Transaction transaction = new Transaction();
		transaction.info("Player is leaving the server. Cleaning up...");
		UUID uuidOfDisconnectingPlayer = event.getPlayerUniqueId();
		loginRequests.remove(uuidOfDisconnectingPlayer);
		UserStorage user = UserStorage.get(uuidOfDisconnectingPlayer);
		if (user == null)
		{
			transaction.end();
			return;
		}
		user.flushState();
		UserStorage.removeUserIfNoTokens(uuidOfDisconnectingPlayer);
		transaction.end();
	}
	
	public void setConnectionJoinResult(UUID uniqueId, boolean value)
	{
		CompletableFuture<Boolean> future = loginRequests.get(uniqueId);
		if (future != null) future.complete(value);
	}
	
	private void authenticateKnownUser(UserStorage user, Audience audience, Transaction transaction)
	{
		OIDCTokens knownTokens = user.tokens();
		
		if (knownTokens == null)
		{
			transaction.warn("The user has no OIDC tokens, initiating new user authentication...");
			audience.showDialog(plugin.createDialog(user.uniqueId(), transaction));
			return;
		}
		TokenUtil tokenUtil = TokenUtil.instance();
		JWT knownIdToken = knownTokens.getIDToken();
		
		if (tokenUtil.isIdTokenValid(knownIdToken, user.nonce(), transaction))
		{
			setConnectionJoinResult(user.uniqueId(), true);
			return;
		}
		
		RefreshToken knownRefreshToken = knownTokens.getRefreshToken();
		TokenRequest newTokenRequest = tokenUtil.tokenRequest(knownRefreshToken);
		TokenResponse newTokenResponse = tokenUtil.tokenResponse(newTokenRequest, transaction);
		
		if (newTokenResponse == null)
		{
			audience.showDialog(plugin.createDialog(user.uniqueId(), transaction));
			return;
		}
		
		OIDCTokens newTokens = tokenUtil.oidcTokens(newTokenResponse, transaction);
		
		if (newTokens == null || !tokenUtil.isIdTokenValid(newTokens.getIDToken(), transaction))
		{
			audience.showDialog(plugin.createDialog(user.uniqueId(), transaction));
			return;
		}
		
		user.updateTokens(newTokens);
		user.flushNonce();
		setConnectionJoinResult(user.uniqueId(), true);
	}
}
