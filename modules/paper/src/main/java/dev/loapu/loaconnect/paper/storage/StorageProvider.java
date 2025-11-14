package dev.loapu.loaconnect.paper.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nimbusds.openid.connect.sdk.Nonce;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import dev.loapu.loaconnect.paper.LoaConnectPlugin;
import net.minidev.json.JSONObject;
import org.apache.commons.io.output.FileWriterWithEncoding;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

public class StorageProvider
{
	private static StorageProvider instance;
	private final LoaConnectPlugin plugin;
	private final File dataDir;
	private final Gson gson;
	
	public static StorageProvider instance()
	{
		return instance == null ? instance = new StorageProvider() : instance;
	}
	
	private StorageProvider()
	{
		plugin = LoaConnectPlugin.instance();
		dataDir = new File(plugin.getDataFolder(), "data");
		gson = new GsonBuilder().setPrettyPrinting().create();
		try
		{
			Files.createDirectories(dataDir.toPath());
		}
		catch (IOException e)
		{
			plugin.getComponentLogger().error("Could not create data directory. Shutting down...", e);
			plugin.getServer().getPluginManager().disablePlugin(plugin);
		}
	}
	
	@SuppressWarnings("unchecked")
	public void saveUser(UserStorage user)
	{
		UUID uniqueId = user.uniqueId();
		OIDCTokens tokens = user.tokens();
		Nonce nonce = user.nonce();
		
		JsonObject fileContents = new JsonObject();
		
		fileContents.addProperty("nonce", nonce == null ? "" : nonce.getValue());
		fileContents.add("tokens", gson.fromJson(tokens.toJSONObject().toString(), JsonElement.class));
		
		File userFile = new File(dataDir, uniqueId.toString() + ".json");
		
		write(userFile, fileContents);
	}
	
	public UserStorage fetchUser(UUID uniqueId)
	{
		File file = new File(dataDir, uniqueId.toString() + ".json");
		
		if (!file.exists()) return null;
		
		JsonObject fileContents = read(file);
		
		try
		{
			assert fileContents != null;
			UserStorage user = UserStorage.add(uniqueId, null, Nonce.parse(fileContents.get("nonce").getAsString()));
			user.updateTokens(OIDCTokens.parse(gson.fromJson(fileContents.get("tokens").toString(), JSONObject.class)));
			return user;
		}
		catch (Exception e)
		{
			plugin.getComponentLogger().error("Could not read user file.", e);
			return null;
		}
	}
	
	private void write(File file, JsonElement fileContents)
	{
		
		try (FileWriterWithEncoding writer = FileWriterWithEncoding.builder().setPath(file.getPath()).setAppend(false).setCharset(StandardCharsets.UTF_8).get())
		{
			gson.toJson(fileContents, writer);
		}
		catch (IOException e)
		{
			plugin.getComponentLogger().error("Could not save user file.", e);
		}
	}
	
	private JsonObject read(File file)
	{
		try (FileReader reader = new FileReader(file))
		{
			return gson.fromJson(reader, JsonObject.class);
		}
		catch (IOException e)
		{
			plugin.getComponentLogger().error("Could not read user file.", e);
		}
		return null;
	}
}
