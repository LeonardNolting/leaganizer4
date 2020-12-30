import matchmaking.Game
import matchmaking.Opponent
import matchmaking.Tournament
import teammaking.Teams

@ExperimentalStdlibApi
fun main() {
	val teams =
		Teams.generateRandom(
			teamSize = 4, playerCount = 44..47, leaderChance = 0.25 /*, minSubstituteCount = 100..200*/
		)
	teams.optimize()
	teams.optimize()
	val opponents = teams.teams.map { Opponent(it, 0) }
	println(Tournament.foo(5, 2, 0, opponents, null))

/*//	repeat(24) {
//		println(it + 1)
//		println((1..24).toList().chunkedPermuted(it + 1).size)
//	}
	println("Hello World!")
	val teams =
		Teams.generateRandom(teamSize = 4, playerCount = 48..51, leaderChance = 0.25*//*, minSubstituteCount = 100..200*//*)
//	println(teams.toPrettyString(0))
	teams.optimize()
//	println(teams.toPrettyString(0))
	teams.optimize()
//	println(teams.toPrettyString(0))
	println("\n\n")
	@Suppress("RemoveExplicitTypeArguments")
	val games: List<Game> = mapOf<Int, Map<Game.State, List<Pair<Int, Int>>>>(
		0 to mapOf(Game.State.FINISHED to listOf(
			0 to 1,
			0 to 2,
			1 to 2,
			3 to 4,
			3 to 5,
			4 to 5,
//			6 to 7,
//			6 to 8,
//			7 to 8,
		)),
		1 to mapOf(Game.State.FINISHED to listOf(
			0 to 3,
		))
	).asGames(teams)
	val opponentsLevel0 = teams.teams.map { Opponent(it, 0) }
	val opponents = opponentsLevel0 + listOf(
		opponentsLevel0[0].copy(level = 1),
		opponentsLevel0[3].copy(level = 1),
//		opponentsLevel0[6].copy(level = 1),
//		opponentsLevel0[3].copy(level = 2)
	)
//	Tournament.plan(games, opponents).forEach(::println)
	val plan = Tournament.plan(
		games = games,
//		games = listOf(),
		opponents = opponents,
//		opponents = opponentsLevel0,
		desiredLevelCount = 1,
		numberOfMatchSizes = 3
	)
	plan.forEach { (level, values) ->
		println("\n\n\n$level:")
		values.forEach { (key, value) ->
			println("$key (${value.size})")
			value.forEach {
				it.forEach {
					println(it)
//				Tournament.plan(
//					rounds = 1,
//					teams = teams.teams,
//					games = games + it
//				).forEach { (key, value) ->
//					print("  ")
//					println(value)
//					key.forEach {
//						it.forEach {
//							print("  ")
//							println(it)
//						}
//						println()
//					}
//				}
				}
				println()
			}
		}
	}
	println(plan.values.sumOf { it.values.sumOf { it.sumOf(List<Game>::size) } })*/
}

private fun Map<Int, Map<Game.State, List<Pair<Int, Int>>>>.asGames(teams: Teams): List<Game> =
	flatMap { (level, states) ->
		states.flatMap { (state, list) ->
			list.map { (id1, id2) ->
				Game(
					state,
					Opponent(teams.teams.find { it.id == id1 }!!, level),
					Opponent(teams.teams.find { it.id == id2 }!!, level)
				)
			}
		}
	}

private fun Iterable<Pair<Int, Int>>.asGames(state: Game.State, teams: Teams, level: Int = 0): List<Game> =
	map { (id1, id2) ->
		Game(
			state,
			Opponent(teams.teams.find { it.id == id1 }!!, level),
			Opponent(teams.teams.find { it.id == id2 }!!, level)
		)
	}