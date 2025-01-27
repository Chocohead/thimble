/*
 * Copyright (c) 2021 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.discord.bot.module.mcversion;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.google.gson.stream.JsonReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import net.fabricmc.discord.bot.DiscordBot;
import net.fabricmc.discord.bot.util.HttpUtil;

public final class McVersionRepo {
	private static final int updatePeriodSec = 120;
	private static final String metaHost = "meta.fabricmc.net";

	private static final Logger LOGGER = LogManager.getLogger(McVersionRepo.class);

	private final List<McVersionUpdateHandler> updateHandlers = new CopyOnWriteArrayList<>();
	private volatile String latest;
	private volatile String latestStable;

	McVersionRepo(DiscordBot bot) {
		bot.getScheduledExecutor().scheduleWithFixedDelay(this::update, 0, updatePeriodSec, TimeUnit.SECONDS);
	}

	public static McVersionRepo get(DiscordBot bot) {
		return ((McVersionModule) bot.getModule("mcversion")).getRepo();
	}

	public @Nullable String getLatest() {
		return latest;
	}

	public @Nullable String getLatestStable() {
		return latestStable;
	}

	/**
	 * Resolve latest/latestStable to the actual versions, null to the latest stable version, otherwise pass through.
	 */
	public @Nullable String resolve(String name) {
		if (name == null || name.equalsIgnoreCase("latestStable")) {
			return latestStable;
		} else if (name.equalsIgnoreCase("latest")) {
			return latest;
		} else {
			return name;
		}
	}

	public void registerUpdateHandler(McVersionUpdateHandler handler) {
		updateHandlers.add(handler);

		String latest = this.latest;
		String latestStable = this.latestStable;

		if (latest != null && latestStable != null) {
			handler.onMcVersionUpdate(latest, latestStable);
		}
	}

	private void update() {
		try {
			HttpResponse<InputStream> response = HttpUtil.makeRequest(metaHost, "/v2/versions/game");
			if (response.statusCode() != 200) throw new IOException("request failed with code "+response.statusCode());

			String latest = null;
			String latestStable = null;

			try (JsonReader reader = new JsonReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
				boolean detectedLatest = false;

				reader.beginArray();

				while (reader.hasNext()) {
					Boolean stable = null;
					String version = null;

					reader.beginObject();

					while (reader.hasNext()) {
						switch (reader.nextName()) {
						case "version" -> version = reader.nextString();
						case "stable" -> stable = reader.nextBoolean();
						default -> reader.skipValue();
						}
					}

					reader.endObject();

					if (stable == null || version == null) {
						throw new IOException("malformed version entry, missing version or stable attribute");
					}

					if (!detectedLatest) {
						latest = version;
						detectedLatest = true;
					}

					if (stable) {
						latestStable = version;
						break;
					}
				}
			}

			if (latest == null || latestStable == null) {
				throw new IOException("no mc version entries");
			} else if (latest.equals(this.latest) && latestStable.equals(this.latestStable)) {
				return;
			}

			this.latest = latest;
			this.latestStable = latestStable;

			for (McVersionUpdateHandler handler : updateHandlers) {
				handler.onMcVersionUpdate(latest, latestStable);
			}
		} catch (Throwable t) {
			LOGGER.warn("MC version retrieval failed", t);
		}
	}

	@FunctionalInterface
	public interface McVersionUpdateHandler {
		void onMcVersionUpdate(String latest, String latestStable);
	}
}
