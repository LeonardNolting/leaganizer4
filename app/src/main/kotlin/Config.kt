import Config.Format.heading
import Config.Format.listHeading
import Config.Format.listUnderlined
import apiResult.ApiResult
import apiResult.HypixelApiResult
import apiResult.MojangApiResult
import com.jessecorbett.diskord.api.model.GuildMember
import com.jessecorbett.diskord.api.model.User
import com.jessecorbett.diskord.dsl.CombinedMessageEmbed
import com.jessecorbett.diskord.dsl.footer
import com.jessecorbett.diskord.util.mention
import com.jessecorbett.diskord.util.withBold
import com.jessecorbett.diskord.util.withItalics
import com.jessecorbett.diskord.util.withSingleLineCode
import com.jessecorbett.diskord.util.withUnderline
import command.*
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import model.type.IGN
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType

object Config {
	val String.johnsonified get() = this.contains("Johnson", ignoreCase = true)
	fun johnsonify(name: String) =
		if (!name.johnsonified) name else "$name Johnson"

	enum class Color(
		val main: Int,
		val light: Int? = null,
		val dark: Int? = null
	) {
		GREEN(
			main = 0x4BB543
		),
		RED(
			main = 0xB80F0A
		),
		BLUE(
			main = 0x0045CF,
			light = 0x2e91fb
		),
		YELLOW(
			main = 0xffd00
		),
		GREY(
			main = 0x333333
		),
	}

	object Known {
		enum class Guild(val id: String) {
			MAIN("772844585751805972")
		}

		enum class Category(val id: String) {
			TOURNAMENT("792805442128314370")
		}

		enum class Channel(val id: String) : BotChannel {
			WELCOME("792084724998602763"),
			REFS("790660195080601610"),
			ANNOUNCEMENTS("773167535490859028"),
			TOURNAMENTS("792094375308034089"),
			TRUSTED("792081169571250176"),
			STAFF("790697608641249310");

			override suspend fun fine(channelId: String, userId: String): Boolean = channelId == id
			val discordChannelClient = bot.clientStore.channels[id]
			override val ids = listOf(id)
		}

		enum class Role(
			val id: String,
			val description: String? = null,
			val managedByBot: Boolean = false,
			val internal: Boolean = false
		) : Allowable {
			EVERYONE(runBlocking { guild.get().roles.find { it.name == "@everyone" }!!.id }, internal = true),
			STAFF("773881546431528981", "Members of the staff.", managedByBot = false),
			HOST("789441047741857823", "Possible tournament hosts.", managedByBot = false),
			VERIFIED(
				"789468609717075990",
				"People who linked their account with hypixel and were verified by the staff.",
				managedByBot = true
			),
			PARTICIPANT("793112879293005864", "Players who take part in the current tournament.", managedByBot = true),
			TRUSTED(
				"790689829896847400",
				"Players that helped the community which were promoted by the staff.",
				managedByBot = true
			),
			LEADER(
				"789916587863703584",
				"Players who are their team's leader in the current tournament.",
				managedByBot = true
			),
			REF("790658858866835466", "Trusted players that can act as referee.", managedByBot = true);

			override fun permitted(member: GuildMember) = member.roleIds.contains(id)
			val discordRole by lazy {
				runBlocking {
					guild.getRole(id)
				}
			}
		}

		enum class User(val id: String, val isSuperUser: Boolean = false) {
			MUNKEL("344125170937626625", isSuperUser = true);

			val discordUser by lazy {
				runBlocking {
					bot.clientStore.discord.getUser(id)
				}
			}
		}
	}

	object Tournaments {
		const val minTeams = 2

		fun customChannelWelcome(team: model.Team) = heading("Hello ${team.name}.") + """"
			:lock:  This is your private temporary channel. You can use it to prepare for the tournament. 
			:no_entry_sign:  At the end of the tournament, the channel and all of its messages will be deleted.
			:microphone2:  You can also use the voice channel for training, if you want.
			
			:four_leaf_clover:  Good luck and have fun!
		""".trimIndent()

		object Trusted {
			fun onPromote(user: User) =
				heading("Welcome ${user.mention}! You're now a trusted player.", suffix = "") + "\n" + listHeading(
					"What does that mean?" to "You have more permissions now. As of now, this mainly means you can be a team leader.",

					"Leader?" to "Each team has one contact person for refs and the host, the leader. They are not part of the staff and only enable a much easier organization. Only trusted persons can be team leaders.",

					"What are my tasks now?" to "Mainly, in some tournaments you'll be picked as a team's leader. You'll be the one in your team to create and lead a hypixel party and communicate with the ref.",

					"Why me?" to "You seem to be experienced with tournaments and capable of responsibly leading a team. That's why the staff decided to trust you.",

					"What now?" to "There's a chance you'll be your teams leader in the next tournament you take part in. Before each tournament starts, a reference will be sent to all leaders. So, right now, there's nothing to worry about! If you are curious though, the command `-reference-leader` will give you the same text right now. If you haven't already, I also recommend reading `-help-formatting-extras`, since this will help you when interacting with the bot.",

					"Nicks." to "Also, you can now set player's nicks. Please note that they're fully managed by the bot - changes you make directly are not saved. To change somebody's nick, use `-settings-nick-set`. `Johnson` - if not present - will be added automatically."
				)

			fun onDemote(user: User, reason: String? = null) =
				"${user.mention} is no longer a trusted player" + if (reason != null) ": $reason" else "."
		}

		object Ref {
			fun onPromote(user: User) =
				heading("Welcome ${user.mention}! You're now a ref.", suffix = "") + "\n" + listHeading(
					"What does that mean" to "You're allowed to moderate games at tournaments and enter the results.",

					"How does that work" to "There is a special channel for refs, and some smart commands. A reference for the exact process of reffing will be sent to every ref some days before each tournament. If you are curious though, the command `-reference-ref` will give you the same text right now.",

					suffix = "?"
				)

			fun onDemote(user: User, reason: String? = null) =
				"${user.mention} is no longer a ref" + if (reason != null) ": $reason" else "."
		}
	}

	object Help {
		suspend fun help() = heading("Help") + """
            For a list of commands, type `-commands`.
            
            :soccer:  We're a *football/soccer* community.
            
            :house:  Our home is *hypixel.net*.
            
            :trophy:  We arrange competitions (*tournaments*), mainly for fun.
            
            :calendar:  This discord server enables better *organization* of these tournaments, in combination with this *bot*.
            
            :bar_chart:  It is responsible for *team management, statistics* and much more.
            
            :link:  You can access it via *commands* (for example `-tournament-participate`) which take parameters (for example `-tournament-participate 2`).
            
            :athletic_shoe:  **First steps**: `-help-firstSteps`
            :pencil:  Usage: `-help-formatting`
            :grey_question:  FAQ: `-help-faq`
            :bulb:  More help: `-help-commands`
            
            :eyes:  If you encounter issues with this bot, please message ${Known.User.MUNKEL.discordUser.mention}.
        """.trimIndent()

		suspend fun firstSteps() = heading("First steps") + """
			We want to make this a peaceful place, without trolling, hacking and spamming.
			Therefore, for most actions, it's essential to know your minecraft account.
			To achieve this, please link your discord and minecraft accounts.
			
			1. Type `/profile` in the in-game chat and press enter
			2. Find the icon called "Social Media"
			3. Find the icon called "Discord"
			4. In the discord app, click your name in the bottom left to copy your Discord tag (e.g. `Munkel#2369`)
			5. In minecraft, paste this copied tag in the chat
			6. If a book pops up, click "I understand"
			7. Tell us you've linked your account by replying `-apply YOUR_MINECRAFT_NAME`
			
			There's also a video (0:33): <https://youtu.be/gqUPbkxxKLI> (additionally, follow step 7)
			
			Source: <https://hypixel.net/threads/guide-how-to-link-discord-account.3315476/>
		""".trimIndent()

		/*suspend fun linking() = heading("Linking") + """
			Video: https://youtu.be/gqUPbkxxKLI
			Text:
			1. Type "/profile" in the in-game chat and press enter
			2. Find the icon called "Social Media"
			3. Find the icon called "Discord"
			4. Go to the Discord app and click on your name on the bottom left to copy your Discord tag
			5. Go back in game and paste that copied tag in the chat
			6. If a book pops up, click "I understand"
			
			Source: https://hypixel.net/threads/guide-how-to-link-discord-account.3315476/
		""".trimIndent()*/

		suspend fun credits() = listHeading(
			"Sources" to listUnderlined(
				"Linking guide" to "https://hypixel.net/threads/guide-how-to-link-discord-account.3315476/",
				"Avatars" to "https://crafatar.com",
				"Trophy icon" to "made by [Freepik](http://www.freepik.com/) from [www.flaticon.com](https://www.flaticon.com/)"
			)
		)

		suspend fun formatting() = heading("Formatting") + """
            This bot is command based, which means, whenever you want to interact with it, you'll use commands.
            Commands make up of 2 parts: Name and parameters (values).
        """.trimIndent() + "\n\n" + listUnderlined(
			"Name" to "A command always begins with a dash (`-`). Its name can consist of multiple parts, which again are added using dashes.",

			"Parameters" to "A command can have unlimited parameters. Each has a name and a type. To add a parameter, use a space character and write its value (e.g. `-name foo`). If you want to use a space inside the value, wrap it with quotes (either `\"` or `'`, e.g. `-name \"hello world\"`).\n**Types**: All parameters are of a certain type. For example, there are \"Text\", \"Number\", \"Mention\" and so on. Worth mentioning is the special type List, which describes a set of values. If you need to set a list of `hello` and `world`, it works like this: `[hello world]`. Note: Lists can also be nested, for example you can have a list of lists of texts.",

			"Command specific help" to "By adding `-help` to the *name* of a command, you'll not call the command but get help on how to use it.\nFirst, a simple description tells you what the command is for.\nSecond, the exact syntax for this command will be shown. This consists of one line, showing how the command is constructed and below a list of all parameters (name and type).",

			"Examples" to "`-help-examples`",

			"Additional features" to "`-help-formatting-extras`",

			"Final note" to "The command system is pretty robust, so there's no need to worry about wrong syntax, insufficient parameters or invalid values - the parser will complain.",

			linebreak = true
		)

		suspend fun formattingExtras() = heading("Formatting extras") + listUnderlined(
			"Explicit naming" to "By default you'll set values for each parameter in a row. But sometimes, you want to set another parameter first, or just be sure the value addresses the correct parameter. This can be done with the following pattern: `name = value`.",

			"Continuous Mode" to "Some commands have quite many parameters, so calling them in just one message is a mess. To avoid this, these use a special feature called \"Continuous mode\". If not all necessary values are given, you can just continue adding missing ones (or even correcting others) by sending further messages - the command will not be called until *all* parameters are given or you use the keyword `submit`.",

			"NamedLists" to "Some commands require to set a name for each list item. As expected, this works the same way you name parameters. Example: `[hello=world foo=bar]`",
		)

		// TODO
		suspend fun tournaments() = """
            tournaments
        """.trimIndent()

		suspend fun definitions(): String {
			fun definitions(vararg pairs: Pair<String, String>) =
				pairs.joinToString("\n") { (name, content) -> "$name: $content" }
			return listHeading(
				"Tournament" to definitions(
					"Tournament" to "A collection* of levels. Usually represented in a *tree diagram*.",
					"Round" to "When you click the NPC in the lobby or enter `/play arcade_soccer` for a tournament, this is a round.",
					"Game" to "A collection* of rounds, where 2 teams play against each other.",
					"Match" to "A collection* of games, for example if 3 teams compete, the match consists of these games: A-B, A-C, B-C.",
					"Level" to "A collection* of matches, a horizontal line on the tree diagram.",
					"Tournament type" to "Tournaments usually are of type `Normal`, but can also be `Volleyball`, `No-Hit` etc., which just affects the rules of a tournament.",
				) + "* collection: many or one.",

				"Persons" to definitions(
					"User" to "Everybody on this server.",
					"Player" to "Users who were verified as a football player.",
					"Participant" to "Player participating in a tournament.",
					"Member" to "Participant with a team.",
					"Substitute" to "Participant without a team. Will play when a member leaves.",
				),
				linebreak = false
			)
		}

		// TODO
		suspend fun faq() = heading("FAQ") + listHeading(
			"How can I link my discord account on hypixel" to "We have short instructions in `-help-linking`. This process usually takes less than a minute to complete.",

			"What is the command to join a round of football" to "`/play arcade_soccer`",

			"How can I get a leader/ref/host" to "If you think you'd be a good fit for this position, please contact the staff.",

			"What is a level/match/round" to "For a list of definitions, please use `-help-definitions`.",

			"Why not use short timezone names?" to "The reason for this and some further information concerning timezones can be found in `-help-timezones`.",

			"I have further questions/need help. Who can I ask" to "First of all, try the `-help` commands (to get a list of them, type `-help-commands`). They often contain useful answers. If that couldn't help you, feel free to message staff or ${Known.User.MUNKEL.discordUser.mention}, who's responsible for this bot.",
			suffix = "?"
		)

		fun roles() = Known.Role.values().joinToString("\n") { role ->
			heading(role.name, linebreak = false) + (role.description ?: "No description provided.") +
				" This role is managed by the ${if (role.managedByBot) "bot" else "staff"}."
		}

		// TODO
		fun timezones() = listHeading(
			"Common timezones" to listOf(
				"America/New_York",
				"Europe/Paris",
			).joinToString("\n") { "- $it" },
			"List of all timezones" to "https://en.wikipedia.org/wiki/List_of_tz_database_time_zones",
			"Why not use short timezone names?" to "Short timezone names are ambiguous, do not include daylight saving and more, that's why it's not recommended to use timezone abbreviations. To reduce writing effort for you, the bot can store your timezone (see your current timezone by `-settings-timezone-get`)."
		)

		// TODO
		fun examples() = heading("Examples") + """
            Examples
        """.trimIndent()
	}

	object Format {
		object Preset {
			fun CombinedMessageEmbed.success() {
				color = Color.GREEN.main
			}

			fun CombinedMessageEmbed.failure() {
				color = Color.RED.main
			}

			fun CombinedMessageEmbed.info() {
				color = Color.BLUE.light
			}

			suspend fun CombinedMessageEmbed.warning() {
				color = Color.YELLOW.main
				footer("If you think this is not correct, please message ${Known.User.MUNKEL.discordUser.username}")
			}
		}

		fun heading(string: String, linebreak: Boolean = true, suffix: String = ": ") =
			(string.capitalize() + suffix).withBold() +
				if (linebreak) "\n" else ""

		fun list(
			vararg pairs: Pair<String, String>,
			separator: String = "\n\n",
			transform: ((Map.Entry<String, String>) -> CharSequence)
		) = pairs.toMap().entries.joinToString(separator) { transform(it) }

		fun listUnderlined(
			vararg pairs: Pair<String, String>,
			linebreak: Boolean = false
		) = list(*pairs) { (key, text) -> "- ${key.withUnderline()}:${if (linebreak) "\n" else " "}$text" }

		fun listHeading(
			vararg pairs: Pair<String, String>,
			linebreak: Boolean = true,
			suffix: String = ""
		) = list(*pairs) { (key, text) -> heading(key, linebreak, suffix) + text }
	}

	object Commands {
		const val prefix = "-"
		val quotes = listOf('"', '\'')
		const val nothing = "NOTHING"

		const val defaultErrorMessage = "Unknown reason."

		object Parameter {
			fun KParameter.toStringDiscord() = "[${displayName!!}]" + if (isOptional) "?" else ""
			fun KParameter.toStringDiscordDetailed() =
				displayName!!.withItalics() + ": " +
					command.Type.get(this.type).config.name.withUnderline() + ". " +
					command.Type.get(this.type).config.description
		}

		private fun defaultException() = Exception("Unknown reason.")

		const val stopped = "Stopped current command."
		fun creatingFailed(e: Throwable = defaultException()) = "Creating command failed:\n" + e.displayMessage
		fun runningFailed(e: Throwable = defaultException()) = "Running command failed:\n" + e.displayMessage
		fun invalidCommand(function: KFunction<*>) =
			"Command `$function` is not valid. Please check that it's not located directly inside a class."

		fun missingParameters(parameters: command.Parameters, text: String) =
			heading(text) + parameters.list(parameters.missing)

		const val notACommand = "Cannot generate Task from message because it's not a command."
		const val notFound = "Command not found."
		const val noneFound = "No commands found."
		const val noPermission = "You don't have permission to run this command."
		fun badChannel(alternativeChannelIds: List<String>) =
			if (alternativeChannelIds.size == 1) "Please only use this command in the channel " +
				alternativeChannelIds.single().toChannelMention() + "."
			else "Please only use this command in one of the following channels: " +
				alternativeChannelIds.joinToString { id -> id.toChannelMention() }

		const val processingParametersFailed = "Processing parameters failed."

		object Parameters {
			const val nothingProhibited = "Value cannot be $nothing."
			const val notAList = "Given parameter is neither a NamedList nor a List."
			const val noEmptyParameter = "Could not insert value because no empty parameter was found."

			/*fun noParameterWithName(parameter: command.Parameters.Parameter) =
				"Could not insert named parameter because no parameter with the name `${parameter.name}` is needed. Ignoring input `${parameter.value}`."*/
			fun noParameterWithName(name: String, value: Any?) =
				"Could not insert named parameter because no parameter with the name `$name` is needed. Ignoring input `$value`."

			fun processingParameterFailed(parameter: command.Parameters.Parameter, e: Exception) =
				"Processing parameter ${parameter.name?.withSingleLineCode() ?: "a"} failed:\n" + e.displayMessage

			val notANamedList =
				"This is not a ${Type.Config.Map.name}, so **no** list item can have a name (don't use `=`)."
			val isANamedList =
				"This is a ${Type.Config.Map.name}, so **every** list item needs to have a name (use `=`)."
		}

		object Type {
			fun noParseMethod(type: command.Type<*, *>) = "No parse method given for type ${type.config.name}."
			fun noMetaMethod(type: command.Type<*, *>) = "No meta method given for type ${type}."
			fun unableToParseMetaType(type: command.Type<*, *>, e: Throwable) =
				"Could not parse expected meta type ${type.config.name}:\n" +
					e.displayMessage

			fun unableToParseType(type: command.Type<*, *>, parameter: command.Parameters.Parameter, e: Exception) =
				"Could not parse expected type (${type.config.name}) from input string `${parameter.value}`:\n" +
					e.displayMessage

			fun noTypeObject(kType: KType) = "No type object given for type ${kType}."

			fun unableToParse(name: String) = "Can't parse $name."

			object Config {
				object Player : command.Type.Config("Mention or In-Game-Name") {
					const val notFound =
						"Player not found. They need to apply first using `-apply`, alternatively staff can also register them using `-register`."
				}

				object Team : command.Type.Config("Team name") {
					const val notFound = "Team not found."
				}

				object Skill : command.Type.Config(
					"Skill",
					"On a scale of 0 to 100, how skilled this user is. Can exceed 100."
				)

				object TimeOptions : command.Type.Config(
					"Time options",
					"How many options participants will have to pick from."
				)

				object TimeInterval : command.Type.Config(
					"Time interval",
					"In which interval time options will be set."
				)

				object ZoneId : command.Type.Config(
					"Timezone",
					"Format: Continent/City",
					listOf("Europe/Berlin", "America/New_York")
				)

				object LocalDateTime : command.Type.Config(
					"Date + Time",
					"Format: ", // TODO
					listOf("2020-12-24 00:00")
				)

				object ZonedDateTime : command.Type.Config(
					"Date + Time (+ Timezone)",
					"Timezone defaults to the one in your settings. Format: ", // TODO
					listOf("2021-01-16 12:53 Europe/Berlin")
				)

				object LocalDate : command.Type.Config(
					"Date",
					"Just a date. Format: ", // TODO
					listOf("2020-12-24")
				)

				object Channel : command.Type.Config("Channel")
				object IGN : command.Type.Config("In-Game-Name", "A minecraft username.")
				object Nick : command.Type.Config("Nickname", "Any nickname. Visible to everyone.")
				object TeamSize : command.Type.Config("Team size")
				object Message : command.Type.Config("Message")
				object User : command.Type.Config("Mention")
				object Int : command.Type.Config("Integer")
				object Float : command.Type.Config("Float")
				object String : command.Type.Config("Text")
				object Boolean : command.Type.Config("Switch")

				object List : command.Type.Config("List")
				object Map : command.Type.Config("NamedList")

				object Participant : command.Type.Config(Player.name) {
					const val notFound = "This player is not currently participating at the current tournament."
				}

				object ListGenerator : command.Type.Config.Generator(
					{ actualKType ->
						val nestedName = actualKType.arguments.firstOrNull()?.type?.typeObject?.config?.name
							?: "Anything"
						command.Type.Config("${List.name}($nestedName)")
					})

				object MapGenerator : command.Type.Config.Generator(
					{ actualKType ->
						val nestedName = actualKType.arguments.getOrNull(1)?.type?.typeObject?.config?.name
							?: "Anything"
						command.Type.Config("${Map.name}($nestedName)")
					})
			}
		}

		abstract class Continuous {
			companion object {
				const val exit = "Exit continuous mode."
				fun info(keywords: List<Command.Mode.Keyword<*>>, parameters: command.Parameters) =
					"Switched to continuous mode. If you need help with this, type `help`.\nPossible keywords:\n" +
						keywords.joinToString("\n") { keyword -> keyword.toStringDiscord() } + "\n\n" + missingParameters(
						parameters,
						"Please fill in these parameters"
					)

				const val defaultCouldRunMessage =
					"All necessary parameters have values. If you want to execute the command, type `submit`."

				fun help(helpAddition: String) = """
                    Messages quickly get messy when you have more than 4 parameters. Continuous mode addresses this problem by allowing you to add parameters 'message by message'. This means, instead of having to write one long text, you can now just type the command and add any parameters afterwards!
                    $helpAddition
                    For example (bot responses omitted):
                    `-tournament-register`
                    `team_size = 1`
                    `max_players = 30`
                """.trimIndent()
			}

			abstract val helpAddition: String

			object Possible : Continuous() {
				override val helpAddition =
					"The command will be automatically executed as soon as all necessary parameters are given!"
			}

			object Always : Continuous() {
				override val helpAddition =
					"Type `submit` to finish the command."
			}

			object Keywords {
				val help = "help" to "Get help with continuous mode."
				val run = "submit" to "Run command."
				val exit = "exit" to "Exit continuous mode."
			}
		}

		object Apply {
			const val description =
				"Apply for being verified by a staff member. This step is necessary to be able to participate at tournaments and more."
			const val onSuccess =
				"The hosts were asked to verify you and set your current tier according to your skill.\nUsually, this doesn't take longer than one day! The bot will send a direct message when you got verified."
			const val alreadyVerified = "Good news! You have already been verified. :grinning:"
			const val alreadyApplied =
				"You have already applied. Please be patient, the hosts will be reminded of unchecked applications."
			const val ignAlreadyUsed = "This ign is already used by a player. Please check the name for typos."
			const val wrongDiscord =
				"The linked Discord account is not yours. Please make sure you link the correct Discord account, check the minecraft username for typos and then retry."

			fun notification(user: User, ign: IGN) =
				"User ${user.mention} (${ign.value}) has applied. Please verify them by using `-verify`, or reject them with `-reject`."
		}

		object Verify {
			const val description = "Verify a player so they can take part at tournaments etc."
			const val onSuccess = "Thanks for verifying. The user was informed."
			fun onSuccessDm(user: User) = """
				:wave: ${user.mention}! You've been verified and can now participate in tournaments.
				
				${heading("Where to go from here", suffix = "?", linebreak = false)}
				First of all, it's a good idea to set your timezone via `-settings-timezone-set`.
				You can participate in tournaments with the command `-tournament-participate`.
				For more help with the bot, use `-help`.
				
				I hope you have a nice time and much fun here!
			""".trimIndent()
		}

		object Reject {
			const val description = "Reject a players application."
			const val onSuccess = "The player has been rejected and was informed."
			fun onSuccessDm(user: User, reason: String?) =
				"${user.mention}'s application has been rejected by the staff" +
					if (reason == null) "."
					else ": $reason"
		}

		object Register {
			const val description = "Apply and verify another user in one step."
			const val onSuccess = "Thanks for verifying. The user was informed."
			fun applyingFailed(e: Exception, userToBeRegistered: User) =
				"Applying ${userToBeRegistered.mention} failed ('you' applies to them, *not* you):\n" + e.displayMessage

			fun verifyingFailed(e: Exception, userToBeRegistered: User) =
				"Verifying ${userToBeRegistered.mention} failed:\n" + e.displayMessage
		}
	}

	abstract class Api<T : ApiResult> {
		suspend fun call() = get(url)
		abstract val url: String
		abstract suspend fun get(url: String): T

		protected fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

		data class Hypixel(private val ign: IGN) : Api<HypixelApiResult>() {
			override val url =
				"https://api.hypixel.net/player?key=${env["HYPIXEL_API_KEY"]!!}&name=" + encode(ign.value)

			override suspend fun get(url: String) = client.get<HypixelApiResult>(url)

			companion object {
				const val noPlayer = "This player doesn't exist or has never been on hypixel."
				const val noDiscord =
					"Please link your discord account on hypixel. If you don't know how to, use `-help-linking`."
			}
		}

		data class Crafatar(
			private val minecraftId: String,
			private val default: String = "MHF_Steve",
		) : Api<Nothing>() {
			override val url = "https://crafatar.com/avatars/${encode(minecraftId)}?overlay&default=${encode(default)}"
			override suspend fun get(url: String) = error("Don't call this API via Ktor.")
		}

		sealed class Mojang<R : MojangApiResult> : Api<R>() {
			protected val base = "https://api.mojang.com/"

			data class UID(
				private val uid: String
			) : Mojang<MojangApiResult.UID>() {
				override val url = "${base}user/profiles/${encode(uid)}/names"
				override suspend fun get(url: String) = client.get<MojangApiResult.UID>(url)
			}

			data class IGN(
				private val ign: model.type.IGN
			) : Mojang<MojangApiResult.IGN>() {
				override val url = "${base}users/profiles/minecraft/${encode(ign.value)}"
				override suspend fun get(url: String) = client.get<MojangApiResult.IGN>(url)
			}
		}
	}
}