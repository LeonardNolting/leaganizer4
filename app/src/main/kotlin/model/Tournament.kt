package model

//import primeFactors
import Config
import Config.Format.Preset.failure
import Config.Format.Preset.info
import Config.Format.Preset.success
import Config.Format.heading
import Temporal
import Temporal.defaultAlternativeZoneIds
import bot
import com.jessecorbett.diskord.api.model.ChannelType
import com.jessecorbett.diskord.api.model.Message
import com.jessecorbett.diskord.api.model.Overwrite
import com.jessecorbett.diskord.api.model.OverwriteType
import com.jessecorbett.diskord.api.model.Permissions
import com.jessecorbett.diskord.api.rest.CreateChannel
import com.jessecorbett.diskord.api.rest.CreateMessage
import com.jessecorbett.diskord.api.rest.EmbedImage
import com.jessecorbett.diskord.api.rest.MessageEdit
import com.jessecorbett.diskord.api.rest.client.ChannelClient
import com.jessecorbett.diskord.dsl.CombinedMessageEmbed
import com.jessecorbett.diskord.dsl.author
import com.jessecorbett.diskord.dsl.embed
import com.jessecorbett.diskord.dsl.field
import com.jessecorbett.diskord.dsl.footer
import com.jessecorbett.diskord.util.mention
import com.jessecorbett.diskord.util.toFileData
import com.jessecorbett.diskord.util.toRoleMention
import com.jessecorbett.diskord.util.toUserMention
import com.jessecorbett.diskord.util.withBold
import com.jessecorbett.diskord.util.withStrikethrough
import command.Builder
import command.Channels
import command.Command
import command.Permission
import command.messageExists
import command.toChannelMention
import command.transactionSuspend
import cutOverflow
import guild
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import matchmaking.Game
import model.database.table.LevelTable
import model.database.table.OpponentTable
import model.database.table.ParticipantTable
import model.database.table.StartPreferredTable
import model.database.table.StartTable
import model.database.table.TeamTable
import model.database.table.TournamentTable
import model.type.TeamSize
import model.type.TimeInterval
import model.type.TimeOptions
import modes
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import send
import teammaking.Teams
import toStringAlternative
import withMultiLineCode
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class Tournament(
	id: EntityID<Int>,
) : IntEntity(id) {
	var teamSize by TournamentTable.teamSize
	var timeOptions by TournamentTable.timeOptions
	var timeInterval by TournamentTable.timeInterval
	var announcementId by TournamentTable.announcementId
	var createdBy by Player referencedOn TournamentTable.createdBy
	var createdAt by TournamentTable.createdAt
	var state by TournamentTable.state
	var open by TournamentTable.open
	var currentLevel by Level optionalReferencedOn TournamentTable.currentLevel

	val teams by Team referrersOn TeamTable.tournament
	val levels by Level referrersOn LevelTable.tournament
	val starts by Start referrersOn StartTable.tournament

	val participants by Participant referrersOn ParticipantTable.tournament
	val activeParticipants get() = participants.filter { it.quit == null }
	val substitutes get() = participants.filter { it.team == null }
	val activeSubstitutes get() = substitutes.filter { it.quit == null }

	val preparedDates get() = starts.all { it.timeOption != null }
	val preparedTeams get() = teams.toList().isNotEmpty()

	val name get() = "Tournament#$id"
	val nameFull get() = ":trophy: $name"

	val description get() = "Team size: ${teamSize.value}\nParticipate: `-tournament-participate`"

	fun timeOptions(start: Start): List<Instant> = List(timeOptions.value) { index ->
		start.start + Duration.ofMinutes((index * timeInterval.value).toLong())
	}

	fun allTimeOptions(starts: List<Start>) =
		starts.map { start -> timeOptions(start) }

	fun times(starts: List<Start>) =
		starts.map { start -> timeOptions(start)[start.timeOption!!] }

	fun formatTimes(alternativeZoneIds: List<ZoneId?> = defaultAlternativeZoneIds) = transaction {
		val starts = starts.toList()
		(if (preparedDates)
			times(starts).withIndex().joinToString("\n") { (index, start) ->
				(index + 1).toStringAlternative() + ". " + Temporal.displayFull(
					start,
					alternativeZoneIds = alternativeZoneIds
				)
			}
		else
			allTimeOptions(starts).joinToString("\n\n") { timeOptions ->
				Temporal.displayDate(timeOptions.first()) + " time options:" +
					timeOptions.withIndex().joinToString("") { (index, start) ->
						"\n${(index + 1).toStringAlternative()}. " + Temporal.displayTimeFull(
							start,
							alternativeZoneIds = alternativeZoneIds
						)
					}
			}).withMultiLineCode("swift")
	}

	fun refreshAnnouncement(full: Boolean = false, reason: String? = null) = transactionSuspend {
		val channel = Config.Known.Channel.TOURNAMENTS.discordChannelClient
		val embed = embed {
			color = state.color

			val title = nameFull
			this.title = if (state == State.ENDED) title.withStrikethrough() else title

			description = this@Tournament.description + "\n" + if (!preparedDates) """
				
				:bar_chart:  You can *vote* for time options within this command.
				
				:clock3:  The exact **times** will be presented **2 days** before the tournament starts!
				:busts_in_silhouette:  Usually, **teams** will also be announced around then.
			""".trimIndent() else ""

			timestamp = Instant.now().toString()
			thumbnail = EmbedImage("attachment://trophy.png")

			field(":calendar: Dates:".withBold(), formatTimes(), false)

			if (preparedTeams) {
				val emoji = ":busts_in_silhouette: "
				val teams = teams.toList().sortedBy { it.id.value }
				if (teams.size > 20)
					field("$emoji Teams:", teams.joinToString("\n") { team ->
						team.name + " : " + team.leader.player.name + if (team.nick != null) " (${team.nick})" else ""
					}, false)
				else {
					teams.forEach { team ->
						field(
							"$emoji ${team.name}" +
								if (team.nick != null) " (${team.nick!!.cutOverflow(12)})" else "",
							team.displayOrNull(showTrusted = false) ?: "Disbanded.",
							true
						)
					}
					field("$emoji Substitutes", activeSubstitutes.map { it.player }.display(), true)
				}
			} else {
				val activeParticipants = activeParticipants.toList()
				var string = activeParticipants.takeLast(4).reversed().map { it.player }.display(withCode = false)
				if (activeParticipants.size > 4) string += "\n..."
				field(
					"Participants: ${activeParticipants.size}",
					string.withMultiLineCode().ifEmpty { "No participants." }, false
				)
			}

			footer(if (reason != null) "Edit: $reason" else "Original version.") {
				author("Host: " + createdBy.ign!!.value) {
					authorImageUrl = Config.Api.Crafatar(createdBy.minecraftId).url
				}
			}
		}

		val announcementExists = if (announcementId == null) false else channel.messageExists(announcementId!!)
		if (announcementId == null || full || !announcementExists) {
			if (announcementId != null && announcementExists) channel.deleteMessage(announcementId!!)
			val message = channel.createMessage(
				CreateMessage("", embed = embed),
				File(ClassLoader.getSystemResource("trophy.png").file).toFileData()
			)

			announcementId = message.id
			message
		} else channel.editMessage(announcementId!!, MessageEdit("", embed))
	}

	suspend fun notify(block: suspend CombinedMessageEmbed.() -> Unit) = send(Config.Known.Channel.ANNOUNCEMENTS, block)

	suspend fun deleteChannels() = teams.forEach { team -> team.deleteChannels() }
	suspend fun deleteRoles() = teams.forEach { it.deleteRole() }

	suspend fun end(reason: String? = null) {
		require(state != State.ENDED) { "This tournament is already ended." }
		require(state != State.FINISHED) { "This tournament has already finished." }
		transactionSuspend {
			state = State.ENDED
			deleteChannels()
			deleteRoles()
		}
		refreshAnnouncement(reason = "Ended tournament" + if (reason == null) "." else ": $reason")
		notify {
			text = "@everyone The current tournament was ended${if (reason == null) "." else ": $reason"} -> ${
				Config.Known.Channel.TOURNAMENTS.id.toChannelMention()
			}"
		}
	}

	/**
	 * Deletes all levels, opponents, games, rounds, teams, preferredStarts, roles, text and voice channels
	 * Does NOT delete participants, starts, tournament
	 * Sets state to registered
	 */
	suspend fun reset(reason: String? = null, notify: Boolean = true) {
		transactionSuspend {
			deleteChannels()
			deleteRoles()

			participants.forEach {
				it.removeStaticTournamentRoles()
				it.resetStatistics()
			}

			levels.forEach { it.delete() }
			// Cascades to opponents
			// Cascades to games
			// Cascades to rounds
			teams.forEach { it.delete() }
			starts.forEach { start ->
				start.preferredStarts.forEach { it.delete() }
				start.timeOption = null
			}
			state = State.REGISTERED
		}

		refreshAnnouncement(true, "Reset" + if (reason == null) "." else ": $reason")

		if (notify) notify {
			text = "${
				Config.Known.Role.PARTICIPANT.id.toRoleMention()
			} Current tournament was reset${if (reason == null) "." else ": $reason"} -> ${
				Config.Known.Channel.TOURNAMENTS.id.toChannelMention()
			}"
		}
	}

	fun distributeTeams() = transaction {
		Teams.distribute(activeParticipants.map { it.toOrganizationParticipant() }.shuffled(), teamSize.value)
	}

	val opponents
		get() = Opponent.find {
			OpponentTable.level inSubQuery LevelTable.slice(LevelTable.id).select { LevelTable.tournament eq id.value }
		}

	fun plan() = transaction {
		val opponents = opponents.toList()
		matchmaking.Tournament.plan(
			opponents.flatMap { opponent -> opponent.games.map { it.toOrganizationGame() } },
			opponents.map { it.toOrganizationOpponent() }
		)
	}

	fun possibilities(plan: Map<Int, Map<Int, List<List<Game>>>> = plan()): List<List<Game>> = plan
		.values // Levels = List<Level> = List<Possibilities> = List<Map<Int, Possibility>>
		.flatMap { it.values } // Levels = List<Level> = List<Possibilities> = List<List<Possibility>>
		.map { it.flatten().distinct() }

	companion object : IntEntityClass<Tournament>(TournamentTable) {
		@Command(
			"tournament",
			description = "Get info about the current tournament."
		)
		fun tournament() = CombinedMessageEmbed().apply {
			info()
			val tournament = currentOrNull
			if (tournament == null) {
				description = "There is no active tournament."
			} else {
				title = tournament.nameFull
				description = tournament.description
			}
		}

		@Command(
			"tournament", "state",
			permission = Permission.VERIFIED,
			description = "Get the state of the current tournament.",
		)
		fun state() = current.state

		@Command(
			"tournament", "register",
			mode = Command.Mode.Continuous.Possible::class,
			permission = Permission.HOST,
			description = "Registers a new tournament.",
			onSuccess = "Registered new tournament.",
			warning = "When registering a tournament, you'll automatically be handled as host.",
			channels = Channels.STAFF
		)
		fun register(
			@Command.Meta
			player: Player,
			@Command.Meta
			zoneId: ZoneId,

			starts: List<ZonedDateTime>,
			teamSize: TeamSize,
			timeOptions: TimeOptions,
			timeInterval: TimeInterval,
		) = transactionSuspend {
			require(currentOrNull == null) { "There is still an active tournament. Please wait for it to end or force-end it first using `-tournament-end`." }
			require(starts.isNotEmpty()) { "A tournament needs at least one start time." }

			val tournament = Tournament.new {
				state = State.REGISTERED
				createdBy = player
				createdAt = Instant.now()
				this.teamSize = teamSize
				this.timeOptions = timeOptions
				this.timeInterval = timeInterval
			}

			starts.forEach { zonedDateTime ->
				Start.new {
					this.tournament = tournament
					this.start = zonedDateTime.toInstant()
				}
			}

			tournament.refreshAnnouncement()
			tournament.notify {
				text =
					"@everyone A new tournament was registered -> ${Config.Known.Channel.TOURNAMENTS.id.toChannelMention()}"
			}
		}

		@Command(
			"tournament", "reset",
			permission = Permission.HOST,
			description = "Reverses preparations. Deletes ALL levels, opponents, games, rounds, teams, preferredStarts, roles, text and voice channels. Does NOT delete participants, starts, tournament. Resets state to REGISTERED.",
			warning = "This action is irreversible.",
			onSuccess = "Reset tournament.",
			channels = Channels.STAFF
		)
		suspend fun reset(reason: String? = null) = current.reset(reason)

		@Command(
			"tournament", "prepare", "dates",
			permission = Permission.HOST,
			description = "Fix tournament dates.",
			onSuccess = "Set dates."
		)
		fun prepareDates() = transactionSuspend {
			val tournament = current
			require(!tournament.preparedDates) { "Dates were already prepared." }
			val timeOptions = tournament.timeOptions
			val starts = tournament.starts
			starts.forEach { start ->
				val preferredStarts = start.preferredStarts
				val votes = List(timeOptions.value) { index -> preferredStarts.count { it.timeOption == index } }
				start.timeOption = votes.indexOf(votes.maxOrNull()!!)
			}
			tournament.refreshAnnouncement(reason = "Chose time options.")
			tournament.notify {
				text =
					"${Config.Known.Role.PARTICIPANT.id.toRoleMention()} The start times for the current tournament were set -> ${Config.Known.Channel.TOURNAMENTS.id.toChannelMention()}"
			}
		}

		/*class PrepareTeamsMode(
			builder: Builder,
			bot: Bot,
			channelId: String,
		) : Command.Mode.Continuous(builder, bot, channelId) {
			fun highestPrimeFactor(size: Int) = current.distributeTeams().first.size.primeFactors.maxOrNull()!!
			fun betterAlternative(size: Int): Int? {
				val now = highestPrimeFactor(size)
				val plusOne = highestPrimeFactor(size + 1)
				val minusOne = highestPrimeFactor(size - 1)
				return when {
					now > plusOne -> betterAlternative(size + 1)
					now > minusOne -> betterAlternative(size - 1)
					else -> null
				}
			}

			override suspend fun couldRun() = builder.isReady
			override suspend fun shouldRun(): Boolean {
				if (!builder.isFilled) return false

				val size = current.distributeTeams().first.size
				val betterAlternative = betterAlternative(size)
				return if (betterAlternative != null) {
					val difference = betterAlternative - size
					val highestPrimeFactor = highestPrimeFactor(size)
					// TODO test if there's one more day to go
					send {
						text =
							"The highest prime factor is $highestPrimeFactor, which means that somewhere in the tournament there'll be $highestPrimeFactor teams playing against each other. This could be improved by ${
								if (difference > 0)
									"waiting for $difference ${if (difference > 0) "more" else "less"} ${if (difference > 1) "teams" else "team"} to join"
								else "allowing ..."
							}. If you want to wait, type `help`, otherwise use `submit`." // TODO
					}
					customKeywords += listOf(
						Keyword(Config.Commands.Continuous.Keywords.exit) {
							send { text = Config.Commands.Continuous.exit }
							exit()
						}
					)
					false
				} else true
			}

			override val helpAddition = Config.Commands.Continuous.Possible.helpAddition

		}*/

		@Command(
			"tournament", "prepare", "teams",
			permission = Permission.HOST,
//			mode = PrepareTeamsMode::class,
			description = "Fix players and matches.",
			onSuccess = "Prepared tournament.",
		)
			/**
			 * gegeben: 42 Spieler
			 * Tommi:   14 Teams
			 * Dann:    Warnung: 14 Teams = 2 * 7 = lange WEGEN 7 > 4
			 *          deswegen Vorschlag 1: abwarten, Nachricht senden "bitte macht noch ein Team"
			 * Host yes:-> BEENDEN; Nachricht senden;
			 * Host no: WEITER
			 *
			 * TEAMS ERSTELLEN
			 *
			 *          deswegen Vorschlag 2: 2 zufällige Teams in nächstes Level befördern
			 * Host yes:-> Game(randomTeam1, null, winner = randomTeam1) & mit randomTeam2 ...; normal fortführen
			 * Host no: -> normal fortführen
			 *
			 * Möchtest du dich vorher entscheiden, wie groß die Gruppengrößen sein sollen?
			 */
		fun prepareTeams(
			/**
			 * Refscount: null = infinite/unknown
			 */
			refsCount: Int? = null
		) = transactionSuspend {
			val tournament = current
			require(!tournament.preparedTeams) { "Teams were already prepared." }
			val activeParticipants = tournament.activeParticipants.toList()
			val minParticipantsSize = tournament.teamSize.value * Config.Tournaments.minTeams
			require(activeParticipants.size > minParticipantsSize) {
				"There are not enough participants to build at least 2 teams.\nPlease either wait for more to join or end this tournament via `-tournament-end`.\nParticipants: ${activeParticipants.size}/$minParticipantsSize"
			}

			val (teams, substitutes) = current.distributeTeams()

			teams.withIndex().forEach { (index, team) ->
				Team.new(
					tournament, index,
					Participant[team.members.filter { member -> member.leader }.random().id],
					team.members.map { Participant[it.id] }
				)
			}

			tournament.notify {
				text = "${
					Config.Known.Role.PARTICIPANT.id.toRoleMention()
				} Teams for the current tournament were set -> ${
					Config.Known.Channel.TOURNAMENTS.id.toChannelMention()
				}"
			}

			tournament.refreshAnnouncement(reason = "Created teams.")
		}

		@Command(
			"tournament", "participants",
			description = "Get a list of all participants."
		)
		fun participants() = transaction {
			current.activeParticipants.map { it.player }.displayFull()
				.ifEmpty { "There are currently no participants." }
		}

		@Command(
			"tournament", "substitutes",
			description = "Get a list of all substitutes.",
		)
		fun substitutes() = transaction {
			current.activeSubstitutes.map { it.player }.displayFull()
				.ifEmpty { "There are currently no substitutes." }
		}

		@Command(
			"tournament", "participate",
			description = "Sign up for the current tournament. Preferred time option will be ignored if the tournament is already prepared.",
			onSuccess = "You signed up for the current tournament. As soon as a time, teams and matches are fixed, you'll be informed!",
		)
		fun participate(
			@Command.Meta
			player: Player,
			@Command.Meta
			channel: ChannelClient,

			preferredTimeOptions: List<Int?> = listOf()
		) = transactionSuspend {
			val tournament = current
			if (tournament.state == State.PREPARED && preferredTimeOptions.isNotEmpty())
				send(channel) {
					text = "Preferred time option will be ignored, since the tournament was already prepared."
				}

			check(player.verified) { "You must be verified to participate at tournaments. Please wait for a staff member to verify you." }

			val existingParticipant = Participant.fromPlayerTournamentActive(player, tournament)
			check(existingParticipant == null) { "You're already participating." }

			val participant = Participant.new {
				this.tournament = tournament
				this.player = player
				this.joined = Instant.now()
			}

			tournament.starts.toList().forEachIndexed { index, start ->
				StartPreferred.new {
					this.participant = participant
					this.start = start
					this.timeOption = preferredTimeOptions.getOrNull(index)?.minus(1)
				}
			}

			guild.addMemberRole(player.discordId, Config.Known.Role.PARTICIPANT.id)
			tournament.refreshAnnouncement(reason = "${player.ign!!.value} joined.")
			participant
		}

		@Command(
			"tournament", "resign",
			permission = Permission.PARTICIPANT,
			description = "Resign from the current tournament.",
			onSuccess = "You resigned from the current tournament.",
			warning = "This action is irreversible."
		)
		suspend fun resign(
			@Command.Meta
			player: Player,
		) {
			val participant = Participant.fromPlayerTournamentActive(player)
				?: error("You don't participate in the current tournament.")
			removeParticipant(player, participant, "They resigned.")
		}

		@Command(
			"tournament", "remove",
			permission = Permission.STAFF,
			description = "Removes a player from the tournament.",
			warning = "This action is irreversible.",
			onSuccess = "Removed player."
		)
		suspend fun removeParticipant(
			player: Player,
			reason: String? = null
		) {
			val participant = Participant.fromPlayerTournamentActive(player)
				?: error("This player doesn't participate in the current tournament.")
			removeParticipant(player, participant, reason)
		}

		private suspend fun removeParticipant(
			player: Player,
			participant: Participant,
			reason: String? = null
		) {
			val tournament = current
			var announcementFullRefresh = false

			transactionSuspend {
				val team = participant.team
				if (team != null) {
					val substitutes = tournament.activeSubstitutes

					if (substitutes.isEmpty() || (participant.isLeader && substitutes.none { it.player.trusted })) {
						println("Leader: " + participant.isLeader)
						println("---")
						substitutes.forEach { println(it.player.name + "; trusted = " + it.player.trusted) }
						team.disband()
						announcementFullRefresh = true
					} else team.substitute(
						participant, Participant[Teams.substitute(
							tournament.teams.map { it.toOrganizationTeam() },
							substitutes.map { it.toOrganizationParticipant() },
							participant.toOrganizationParticipant()
						).id]
					)
				}
				participant.quit = Instant.now()

				StartPreferred.find {
					StartPreferredTable.participant eq participant.id
				}.forEach { it.delete() }

				participant.removeStaticTournamentRoles()
			}
			tournament.refreshAnnouncement(
				announcementFullRefresh,
				reason = "${player.name} was removed" + if (reason == null) "." else ": $reason"
			)
		}

		@Command(
			"tournament", "disbandTeam",
			permission = Permission.STAFF,
			description = "Disbands a team.",
			onSuccess = "Disbanded team.",
			warning = "This action is irreversible.",
		)
		suspend fun disbandTeam(
			team: Team
		) = team.disband()

		@Command(
			"tournament", "dates",
			description = "Shows all (possible) starts of this tournament.",
		)
		fun allTimeOptions(
			@Command.Meta
			zoneId: ZoneId?
		) = heading("Dates") + current.formatTimes(listOf(zoneId))

		@Command(
			"tournament", "end",
			description = "Ends the current tournament immediately.",
			permission = Permission.HOST,
			onSuccess = "Ended tournament.",
			channels = Channels.STAFF,
			warning = "This action is irreversible."
		)
		suspend fun end(
			reason: String? = null
		) = current.end(reason)

		@Command(
			"ref", "possibilities",
			description = "Get all games that could be played.",
			permission = Permission.REF,
			channels = Channels.REFS,
		)
		fun possibilities() = transaction {
			current.possibilities().withIndex().joinToString("\n\n") { (index, games) ->
				"Possibility ${index + 1}:\n".withBold() + games.joinToString { organizationGame ->
					val team1 = Team[organizationGame.team1.team.id]
					val team2 = Team[organizationGame.team2.team.id]
					team1.name + " *vs.* " + team2.name
				}
			}
		}

		class PlayMode(builder: Builder<Data>, data: Data) : Command.Mode.Keywords<PlayMode.Data>(builder, data) {
			class Data(
				val ref: Player,
				val opponent1: Opponent,
				val opponent2: Opponent, channelId: String
			) : Command.Mode.Data(channelId)

			class Round {
				val goals = mutableListOf<Participant>()
				val assists = mutableListOf<Participant>()
				val cleanSheets = mutableListOf<Participant>()
				val fouls = mutableListOf<Participant>()

				override fun toString() = "[goals: $goals, assists: $assists, cleanSheets: $cleanSheets, fouls: $fouls]"
			}

			init {
				runBlocking {
					send(data.channelId) {
						title = data.opponent1.team.name + " vs. " + data.opponent2.team.name
						field(
							data.opponent1.team.leader.player.discordId.toUserMention(),
							data.opponent1.team.currentMembers.filter { it != data.opponent1.team.leader }
								.joinToString("\n") {
									"/party invite ${it.player.ign!!.value}"
								}, true
						)
						field(
							data.opponent2.team.leader.player.discordId.toUserMention(),
							data.opponent2.team.currentMembers.filter { it != data.opponent2.team.leader }
								.joinToString("\n") {
									"/party invite ${it.player.ign!!.value}"
								}, true
						)
						field(
							data.ref.discordId.toUserMention(),
							"You can use the following keywords:", false
						)
					}
				}
			}

			private var reminded = false

			private val participants = transaction {
				data.opponent1.team.currentMembers + data.opponent2.team.currentMembers
			}

			private fun parse(string: String) =
				participants.find { it.player.ign!!.value == string }
					?: error("Couldn't parse IGN from input `$string`.")

			private val finishedRounds = mutableListOf<Round>()
			private var currentRound: Round? = null

			private suspend fun currentRound(block: suspend (Round) -> Unit) {
				if (currentRound == null) send {
					text = "You haven't started any round."
				} else {
					block(currentRound!!)
				}
			}

			private suspend fun close(embed: suspend CombinedMessageEmbed.() -> Unit) {
				bot.clientStore.channels[data.channelId].delete()
				send(Config.Known.Channel.REFS) {
					embed()
					description += "\n\nNew possibilities will be sent in a second."
				}
				delay(1000)
				send(Config.Known.Channel.REFS) {
					text = possibilities()
				}
			}

			private fun save() = builder.parameters.set("rounds", finishedRounds)

			override val keywords = listOf<Keyword<Data>>(
				Keyword("submit", "Finish this game.") {
					save()
					run()
					close {
						success()
						description =
							"${data.ref.user.mention} finished ${data.opponent1.team.name} *vs.* ${data.opponent2.team.name}."
					}
				},
				Keyword("exit", "Leave this mode. ALL PROGRESS WILL BE LOST!") {
					close {
						failure()
						description =
							"${data.ref.user.mention} cancelled ${data.opponent1.team.name} *vs.* ${data.opponent2.team.name}."
					}
				},
				Keyword("start", "Start a round.") {
					if (currentRound != null) {
						send {
							text = "You're still in a round. Finish it using `finish`."
						}
					} else {
						currentRound = Round()
						send {
							text = "Started new round."
						}
					}
				},
				Keyword("goal", "Register a goal.", alias = listOf("g")) { string ->
					currentRound {
						it.goals += parse(string)
					}
				},
				Keyword("assist", "Register an assist.", alias = listOf("a")) { string ->
					currentRound {
						it.assists += parse(string)
					}
				},
				Keyword(
					"clean sheet", "Register a clean sheet.", alias = listOf(
						"cleansheet",
						"clean_sheet",
						"cs",
						"c"
					)
				) { string ->
					currentRound {
						it.cleanSheets += parse(string)
					}
				},
				Keyword("foul", "Register a foul.", alias = listOf("a")) { string ->
					currentRound {
						it.fouls += parse(string)
					}
				},
				Keyword("finish", "Finish a round.") {
					currentRound {
						finishedRounds += it
						currentRound = null
					}
				}
			)

			override suspend fun processNotKeyword(message: Message) {
				if (!reminded) send {
					text = "Keyword not found. **Possible keywords:**" +
						keywords.joinToString { "\n" + it.toStringDiscord() } +
						"\n\n**This was the last time you've been reminded, so this doesn't interrupt any conversations.**"
				}
				reminded = true
			}
		}

		@Command
		fun registerGame(
			rounds: List<PlayMode.Round>
		) = rounds.joinToString("\n")

		@Command(
			"ref", "play",
			description = "Play a game for the tournament.",
			permission = Permission.REF,
			channels = Channels.REFS
		)
		fun play(
			@Command.Meta
			ref: Player,

			team1: Team,
			team2: Team,
		) = transactionSuspend {
			val opponent1 = team1.currentOpponent
			val opponent2 = team2.currentOpponent
			require(opponent1.level.number == opponent2.level.number) {
				"One team has already won a game in the level of the other team, thus they can't play against each other."
			}

			require(current.possibilities().any { possibility ->
				possibility.any { game ->
					(game.team1.team.id == opponent1.team.id.value && game.team2.team.id == opponent2.team.id.value) ||
						(game.team2.team.id == opponent1.team.id.value && game.team1.team.id == opponent2.team.id.value)
				}
			}) { "This game is not possible" }

			val channel = guild.createChannel(
				CreateChannel(
					team1.name + " vs. " + team2.name,
					ChannelType.GUILD_TEXT,
					categoryId = Config.Known.Category.TOURNAMENT.id
				)
			)
			bot.clientStore.channels[channel.id].editPermissions(
				Overwrite(
					Config.Known.Role.EVERYONE.id,
					OverwriteType.ROLE,
					allowed = Permissions.NONE,
					denied = Permissions.ALL
				),
			)
			listOf(ref, team1.leader.player, team2.leader.player).forEach {
				bot.clientStore.channels[channel.id].editPermissions(
					Overwrite(
						it.discordId, OverwriteType.MEMBER, allowed = Permissions.of(
							com.jessecorbett.diskord.api.model.Permission.VIEW_CHANNEL,
							com.jessecorbett.diskord.api.model.Permission.SEND_MESSAGES,
							com.jessecorbett.diskord.api.model.Permission.EMBED_LINKS,
							com.jessecorbett.diskord.api.model.Permission.ATTACH_FILES,
							com.jessecorbett.diskord.api.model.Permission.READ_MESSAGE_HISTORY,
							com.jessecorbett.diskord.api.model.Permission.MENTION_EVERYONE,
							com.jessecorbett.diskord.api.model.Permission.USE_EXTERNAL_EMOJIS,
							com.jessecorbett.diskord.api.model.Permission.ADD_REACTIONS,
						), denied = Permissions.NONE
					)
				)
			}

			modes += PlayMode(
				Builder(
					Builder.Data(ref.user, bot.clientStore.channels[channel.id]),
					::registerGame,
				),
				PlayMode.Data(
					ref,
					opponent1,
					opponent2,
					channel.id
				)
			)



			"A channel was opened for you: ${channel.id.toChannelMention()}"
		}

		val currentOrNull
			get() = transaction {
				Tournament.find {
					TournamentTable.state inList listOf(State.REGISTERED, State.PREPARED, State.STARTED)
				}.limit(1).firstOrNull()
			}

		val current get() = currentOrNull ?: error("There is no registered tournament at the moment.")
	}

	enum class State(val color: Int) {
		REGISTERED(Config.Color.BLUE.light!!),
		PREPARED(Config.Color.YELLOW.main),
		STARTED(Config.Color.GREEN.main),
		ENDED(Config.Color.RED.main),
		FINISHED(Config.Color.GREY.main)
	}

	/*object Plan {
		data class Generated(
			val width: Int,
			val height: Int,
			val xml: String
		)

		private fun svg(
			x: Int,
			y: Int,
			height: Int,
			width: Int,
			xml: String,
		) = "<svg x='$x' y='$y' height='$height' width='$width'>$xml</svg>"

		private fun team(team: Team): Generated {
			val size = 60
			val xml = "<circle cx='50%' cy='50%' r='40%' stroke='black' stroke-width='10%' fill='white'/>"
			return Generated(size, size, xml)
		}

		private fun match(match: Match): Generated {
			val teams: List<Team> = match.opponents.map { it.team }
			var x = 0
			var y = 0
			var xml = ""
			teams.forEach { team ->
				val generated = team(team)
				xml += svg(x, y, generated.height, generated.width, generated.xml)
				x += generated.width
				if (generated.height > y) y = generated.height
			}
			return Generated(x, y, xml)
		}

		private fun level(level: Level): Generated {
			val matches = level.matches
			var x = 0
			var y = 0
			var xml = ""
			matches.forEach { match ->
				val generated = match(match)
				xml += svg(x, y, generated.height, generated.width, generated.xml)
				x += generated.width
				if (generated.height > y) y = generated.height
			}
			return Generated(x, y, xml)
		}

		fun generate(tournament: Tournament) = transaction {
			val levels = tournament.levels

			var x = 0
			var y = 0
			var xml = ""
			levels.forEach { level ->
				val generated = level(level)
				xml += svg(x, y, generated.height, generated.width, generated.xml)
				y += generated.height
				if (generated.height > x) x = generated.width
			}

			*//*val content = svg(0, 0, x, y, xml)
			val input = TranscoderInput(content)
			val outputStream = FileOutputStream("svg.png")
			val output = TranscoderOutput(outputStream)
			val converter = PNGTranscoder()*//*

			PNGTranscoder().transcode(
				TranscoderInput(svg(0, 0, x, y, xml)),
				TranscoderOutput(FileOutputStream("svg.png"))
			)
		}
	}*/
}