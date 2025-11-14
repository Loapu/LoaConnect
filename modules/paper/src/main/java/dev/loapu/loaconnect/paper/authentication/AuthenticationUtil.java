package dev.loapu.loaconnect.paper.authentication;

import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.openid.connect.sdk.AuthenticationResponse;
import com.nimbusds.openid.connect.sdk.AuthenticationResponseParser;
import com.nimbusds.openid.connect.sdk.AuthenticationSuccessResponse;
import dev.loapu.loaconnect.paper.LoaConnectPlugin;

import java.net.URI;

public class AuthenticationUtil
{
	private static AuthenticationUtil instance;
	private final LoaConnectPlugin plugin;
	
	public static AuthenticationUtil instance()
	{
		return instance == null ? instance = new AuthenticationUtil() : instance;
	}
	
	private AuthenticationUtil()
	{
		plugin = LoaConnectPlugin.instance();
	}
	
	public AuthenticationSuccessResponse authenticationSuccessResponse(URI redirectedUri)
	{
		try
		{
			AuthenticationResponse authResponse = AuthenticationResponseParser.parse(redirectedUri);
			
			if (!authResponse.indicatesSuccess()) throw new RuntimeException("The callback does not indicate success");
			
			return authResponse.toSuccessResponse();
		}
		catch (ParseException | RuntimeException e)
		{
			plugin.getComponentLogger().error("An authentication response was not successful: {}", e.getMessage());
			return null;
		}
		
		
	}
}
