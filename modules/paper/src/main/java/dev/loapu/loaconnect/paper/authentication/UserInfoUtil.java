package dev.loapu.loaconnect.paper.authentication;

import com.nimbusds.oauth2.sdk.ParseException;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.token.BearerAccessToken;
import com.nimbusds.openid.connect.sdk.UserInfoRequest;
import com.nimbusds.openid.connect.sdk.UserInfoResponse;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import dev.loapu.loaconnect.paper.LoaConnectPlugin;
import dev.loapu.loaconnect.paper.storage.UserStorage;

import java.io.IOException;
import java.net.URI;
import java.util.List;

public class UserInfoUtil
{
	private static UserInfoUtil instance;
	private final LoaConnectPlugin plugin;
	private final URI userInfoEndpointURI;
	
	public static UserInfoUtil instance()
	{
		return instance == null ? instance = new UserInfoUtil() : instance;
	}
	
	private UserInfoUtil()
	{
		plugin = LoaConnectPlugin.instance();
		userInfoEndpointURI = plugin.providerMetadata().getUserInfoEndpointURI();
	}
	
	public List<String> groups(UserStorage user)
	{
		BearerAccessToken token = user.tokens().getBearerAccessToken();
		try
		{
			HTTPResponse response = new UserInfoRequest(userInfoEndpointURI, token).toHTTPRequest().send();
			UserInfoResponse userInfoResponse = UserInfoResponse.parse(response);
			if (!userInfoResponse.indicatesSuccess())
			{
				throw new RuntimeException(userInfoResponse.toErrorResponse().getErrorObject().getDescription());
			}
			return userInfoResponse.toSuccessResponse().getUserInfo().getStringListClaim("groups");
		}
		catch (IOException | ParseException e)
		{
			return List.of();
		}
	}
}
