package dev.loapu.loaconnect.paper.authentication;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.AuthorizationGrant;
import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.RefreshTokenGrant;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.TokenResponse;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier;
import com.nimbusds.oauth2.sdk.token.RefreshToken;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;
import dev.loapu.loaconnect.paper.LoaConnectPlugin;
import dev.loapu.loaconnect.paper.transactions.Transaction;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;

public class TokenUtil
{
	private static TokenUtil instance;
	private final LoaConnectPlugin plugin;
	private final URI tokenEndpointURI;
	private final ClientID clientID;
	private final ClientSecretBasic clientSecretBasic;
	private final CodeVerifier codeVerifier = new CodeVerifier();
	
	public static TokenUtil instance()
	{
		return instance == null ? instance = new TokenUtil() : instance;
	}
	
	private TokenUtil()
	{
		plugin = LoaConnectPlugin.instance();
		tokenEndpointURI = plugin.providerMetadata().getTokenEndpointURI();
		clientID = plugin.configProvider().idp().clientId();
		Secret clientSecret = plugin.configProvider().idp().clientSecret();
		clientSecretBasic = new ClientSecretBasic(clientID, clientSecret);
		
	}
	
	public TokenRequest tokenRequest(AuthorizationCode authCode)
	{
		return tokenRequest(new AuthorizationCodeGrant(authCode, plugin.configProvider().general().callbackUri(), codeVerifier));
	}
	
	public TokenRequest tokenRequest(RefreshToken refreshToken)
	{
		return tokenRequest(new RefreshTokenGrant(refreshToken));
	}
	
	private TokenRequest tokenRequest(AuthorizationGrant authGrant)
	{
		return new TokenRequest.Builder(
			tokenEndpointURI,
			clientSecretBasic,
			authGrant
		).scope(plugin.configProvider().idp().scope()).build();
	}
	
	public TokenResponse tokenResponse(TokenRequest tokenRequest, Transaction transaction)
	{
		transaction.info("Requesting new OIDC token.");
		try
		{
			HTTPRequest httpRequest = tokenRequest.toHTTPRequest();
			httpRequest.setConnectTimeout(2000);
			httpRequest.setReadTimeout(2000);
			HTTPResponse httpResponse = httpRequest.send();
			return OIDCTokenResponseParser.parse(httpResponse);
		}
		catch (IOException | ParseException e)
		{
			transaction.error("An error occurred while trying to request an OIDC Token: {}", e.getMessage());
			transaction.error("Issuer IP: {}", plugin.issuerIp());
			return null;
		}
	}
	
	public boolean isIdTokenValid(JWT idToken, Nonce nonce, Transaction transaction)
	{
		transaction.info("Checking ID token validity...");
		try
		{
			IDTokenValidator validator = new IDTokenValidator(
				plugin.configProvider().idp().issuer(),
				clientID,
				JWSAlgorithm.RS256,
				plugin.providerMetadata().getJWKSetURI().toURL());
			validator.validate(idToken, nonce);
			transaction.info("ID token valid.");
			return true;
		}
		catch (BadJOSEException e)
		{
			transaction.warn("The id token is no longer valid.");
		}
		catch (JOSEException e)
		{
			transaction.error("A JOSE exception occurred while validating the id token.");
			transaction.error("Issuer IP: {}", plugin.issuerIp());
		}
		catch (MalformedURLException | IllegalArgumentException e)
		{
			transaction.error("The provided JWKSetURI is invalid. Please check your OpenID Connect provider configuration.");
		}
		return false;
	}
	
	public boolean isIdTokenValid(JWT idToken, Transaction transaction)
	{
		return isIdTokenValid(idToken, null, transaction);
	}
	
	public OIDCTokens oidcTokens(TokenResponse tokenResponse, Transaction transaction)
	{
		if (!tokenResponse.indicatesSuccess())
		{
			transaction.error("A token request was not successful: {}", tokenResponse.toErrorResponse().toJSONObject().toJSONString());
			return null;
		}
		return tokenResponse.toSuccessResponse().getTokens().toOIDCTokens();
	}
	
	public CodeVerifier codeVerifier()
	{
		return codeVerifier;
	}
}
