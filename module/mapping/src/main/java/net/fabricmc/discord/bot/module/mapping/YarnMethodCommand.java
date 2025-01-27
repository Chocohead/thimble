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

package net.fabricmc.discord.bot.module.mapping;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.fabricmc.discord.bot.command.Command;
import net.fabricmc.discord.bot.command.CommandContext;
import net.fabricmc.discord.bot.message.Paginator;
import net.fabricmc.discord.bot.module.mapping.mappinglib.MappingTree.MethodMapping;

public final class YarnMethodCommand extends Command {
	private final MappingRepository repo;

	YarnMethodCommand(MappingRepository repo) {
		this.repo = repo;
	}

	@Override
	public String name() {
		return "yarnmethod";
	}

	@Override
	public List<String> aliases() {
		return List.of("ym", "method");
	}

	@Override
	public String usage() {
		return "<methodName> [latest | latestStable | <mcVersion>]";
	}

	@Override
	public boolean run(CommandContext context, Map<String, String> arguments) throws Exception {
		String mcVersion = arguments.get("mcVersion");
		if (mcVersion == null) mcVersion = arguments.get("unnamed_1");

		MappingData data = YarnCommandUtil.getMappingData(repo, mcVersion);
		String name = arguments.get("methodName");
		Collection<MethodMapping> results = data.findMethods(name);

		if (results.isEmpty()) {
			context.channel().sendMessage("no matches for the given method name and MC version");
			return true;
		}

		Paginator.Builder builder = new Paginator.Builder(context.author())
				.title("%s matches", data.mcVersion);

		for (MethodMapping result : results) {
			builder.page("**Class Names**\n\n**Official:** `%s`\n**Intermediary:** `%s`\n**Yarn:** `%s`\n\n"
					+ "**Method Names**\n\n**Official:** `%s`\n**Intermediary:** `%s`\n**Yarn:** `%s`\n\n"
					+ "**Yarn Method Descriptor**\n\n```%s```\n\n"
					+ "**Yarn Access Widener**\n\n```accessible\tmethod\t%s\t%s\t%s```",
					result.getOwner().getName("official"),
					result.getOwner().getName("intermediary"),
					result.getOwner().getName("named"),
					result.getName("official"),
					result.getName("intermediary"),
					result.getName("named"),
					result.getDesc("named"),
					result.getOwner().getName("named"),
					result.getName("named"),
					result.getDesc("named"));
		}

		builder.buildAndSend(context.channel());

		return true;
	}
}
