package dev.loapu.loaconnect.paper.webserver;

import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.id.State;
import com.nimbusds.openid.connect.sdk.AuthenticationSuccessResponse;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.loapu.loaconnect.paper.LoaConnectPlugin;
import dev.loapu.loaconnect.paper.authentication.AuthenticationUtil;
import dev.loapu.loaconnect.paper.authentication.TokenUtil;
import dev.loapu.loaconnect.paper.storage.UserStorage;
import dev.loapu.loaconnect.paper.transactions.Transaction;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.Executors;

public class ServerProvider
{
	private static ServerProvider instance;
	
	private HttpServer server;
	private final LoaConnectPlugin plugin;
	
	public static ServerProvider instance()
	{
		return instance == null ? instance = new ServerProvider() : instance;
	}
	
	private ServerProvider()
	{
		this.plugin = LoaConnectPlugin.instance();
	}
	
	public void start()
	{
		if (server != null) return;
		
		Transaction httpServerTransaction = new Transaction();
		
		try
		{
			httpServerTransaction.info("Starting webserver on port {}...", plugin.configProvider().general().port());
			server = HttpServer.create(new InetSocketAddress(plugin.configProvider().general().port()), 0);
			server.createContext("/callback", this::handleCallback);
			server.setExecutor(Executors.newSingleThreadExecutor());
			server.start();
			httpServerTransaction.info("Success!");
			httpServerTransaction.end();
		}
		catch (Exception e)
		{
			httpServerTransaction.error("Webserver could not be started: {}", e.getMessage());
			httpServerTransaction.error("Shutting down...");
			httpServerTransaction.end();
			plugin.getServer().getPluginManager().disablePlugin(plugin);
		}
	}
	
	public void stop()
	{
		if (server != null) server.stop(0);
	}
	
	private void handleCallback(HttpExchange exchange)
	{
		String path = exchange.getRequestURI().toString();
		URI requestURI = URI.create(plugin.configProvider().general().baseUrl() + path);
		Transaction transaction = new Transaction();
		transaction.info("Receiving callback: {}", requestURI);
		AuthenticationUtil authUtil = AuthenticationUtil.instance();
		
		AuthenticationSuccessResponse successResp = authUtil.authenticationSuccessResponse(requestURI);
		
		if (successResp == null)
		{
			transaction.error("Callback was invalid.", exchange);
			transaction.end();
			return;
		}
		
		State state = successResp.getState();
		UserStorage user = UserStorage.get(state);
		
		if (user == null)
		{
			transaction.error("Unknown state.", exchange);
			transaction.end();
			return;
		}
		
		user.flushState();
		transaction.info("State is valid. Changing context...");
		String oldTransaction = transaction.transactionId();
		transaction.end();
		transaction = Transaction.byState(state);
		assert transaction != null;
		
		transaction.info("Change of context from transaction {} complete!", oldTransaction);
		
		UUID uuidOfConnectingPlayer = user.uniqueId();
		TokenUtil tokenUtil = TokenUtil.instance();
		AuthorizationCode code = successResp.getAuthorizationCode();
		TokenRequest newTokenRequest = tokenUtil.tokenRequest(code);
		TokenResponse newTokenResponse = tokenUtil.tokenResponse(newTokenRequest, transaction);
		
		if (newTokenResponse == null)
		{
			transaction.error("Invalid newTokenResponse.", exchange);
			plugin.connectionListener().setConnectionJoinResult(uuidOfConnectingPlayer, false);
			return;
		}
		
		OIDCTokens newTokens = tokenUtil.oidcTokens(newTokenResponse, transaction);
		
		if (newTokens == null || !tokenUtil.isIdTokenValid(newTokens.getIDToken(), user.nonce(), transaction))
		{
			transaction.error("Invalid id token.", exchange);
			plugin.connectionListener().setConnectionJoinResult(uuidOfConnectingPlayer, false);
			return;
		}
		user.updateTokens(newTokens);
		transaction.info("Anmeldung erfolgreich! Du kannst diesen Browsertab nun schließen.", exchange);
		plugin.connectionListener().setConnectionJoinResult(user.uniqueId(), true);
	}
}
