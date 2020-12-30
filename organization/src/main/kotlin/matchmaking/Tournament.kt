package matchmaking

typealias Levels = Int

//@formatter:off
val PRIMES_UNTIL_999 = arrayOf(
	2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67,
	71, 73, 79, 83, 89, 97, 101, 103, 107, 109, 113, 127, 131, 137, 139, 149, 151, 157, 163,
	167, 173, 179, 181, 191, 193, 197, 199, 211, 223, 227, 229, 233, 239, 241, 251, 257, 263, 269,
	271, 277, 281, 283, 293, 307, 311, 313, 317, 331, 337, 347, 349, 353, 359, 367, 373, 379, 383,
	389, 397, 401, 409, 419, 421, 431, 433, 439, 443, 449, 457, 461, 463, 467, 479, 487, 491, 499,
	503, 509, 521, 523, 541, 547, 557, 563, 569, 571, 577, 587, 593, 599, 601, 607, 613, 617, 619,
	631, 641, 643, 647, 653, 659, 661, 673, 677, 683, 691, 701, 709, 719, 727, 733, 739, 743, 751,
	757, 761, 769, 773, 787, 797, 809, 811, 821, 823, 827, 829, 839, 853, 857, 859, 863, 877, 881,
	883, 887, 907, 911, 919, 929, 937, 941, 947, 953, 967, 971, 977, 983, 991, 997
)
//@formatter:on

val Int.isPrime
	get() = this in PRIMES_UNTIL_999

val Int.factors: List<Int>
	get() = (1..this).filter { this % it == 0 }

val Int.primeFactors: List<Int>
	get() {
		if (isPrime) return listOf(this)
		var copy = this
		val factors = mutableListOf<Int>()
		var smallestPrimeFactor: Int?
		while (true) {
			smallestPrimeFactor = PRIMES_UNTIL_999.find { copy % it == 0 }
			if (smallestPrimeFactor == null) break
			factors.add(smallestPrimeFactor)
			copy /= smallestPrimeFactor
		}
		return factors
	}

val Int.factorPairs: List<Pair<Int, Int>>
	get() = factors.map { it to this / it }

val <T> List<T>.combinations: List<Pair<T, T>>
	get() {
		val result = mutableListOf<Pair<T, T>>()
		var i = -1
		while (++i < size - 1) {
			var j = i
			while (++j < size) {
				result.add(this[i] to this[j])
			}
		}
		return result
	}

fun <T> Iterable<T>.sequential(maxSynchronous: Int? = null, canBeSynchronous: (a: T, b: T) -> Boolean): List<List<T>> {
	val result = mutableListOf<MutableList<T>>()
	infix fun T.fits(collection: Collection<T>) =
		collection.size < (maxSynchronous ?: collection.size + 1) && collection.all { canBeSynchronous(this, it) }

	forEach { t ->
		val list = result.find { t fits it } ?: result.run {
			val newList = mutableListOf<T>()
			add(newList)
			newList
		}
		list.add(t)
	}

	return result
}

object Tournament {
	fun plan(
		games: List<Game> = emptyList(),
		opponents: List<Opponent>,
		teamCount: Map<Int, Int> = opponents.filterNot { it.isPushed }.groupBy(Opponent::level)
			.mapValues { it.value.size },
		currentLevel: Int = 0,
		// config
		desiredLevelCount: Levels = 1,
		removeExisting: Boolean = true,
		numberOfMatchSizes: Int = 0,
//		permutedAndRaw: Boolean = true
	): Map<Int, Map<Int, List<List<Game>>>> {
		val availableOpponents = opponents.filterNot { it.isPushed }.filterAvailable(games)

		val opponentLevels = availableOpponents.map { it.level }.distinct()

		val networks = games.networks(opponentLevels)

		val finishedNetworks = networks.filterFinished(availableOpponents)

		finishedNetworks.checkIfFinished(games)

		val symmetricalNetworkSizesAndCounts = teamCount.getSymmetricalNetworkSizesAndCounts(networks, finishedNetworks)

		val validSymmetricalNetworkSizesAndCounts: Map<Int, List<Pair<Int, Int>>> =
			symmetricalNetworkSizesAndCounts.filterValid(desiredLevelCount, currentLevel)

		val networkSizeChunkedOpponents: Map<Int, Map<Int, List<List<Opponent>>>> =
			availableOpponents
				.groupBy(Opponent::level)
				.mapValues { (level, opponents) ->
					validSymmetricalNetworkSizesAndCounts[level]!!
						.take(
							if (numberOfMatchSizes <= 0)
								validSymmetricalNetworkSizesAndCounts.size
							else
								numberOfMatchSizes
						)
						.map { (size, _) -> size to opponents.chunkedPermuted(size) }
						.toMap()
				}

		return networkSizeChunkedOpponents
			.mapValues { (_, value) ->
				value
					.mapValues { (_, chunks) ->
						chunks
							.map { it.combinations.asGames(Game.State.READY) }
							.map { if (removeExisting) it - games else it }
//							.filter { it.isNotEmpty() }
					}
//					.filterValues { it.isNotEmpty() }
					.toMap()
			}.toMap()
	}

	fun reconstruct(
		games: List<Game> = emptyList(),
		opponents: List<Opponent>,
	): List<Level> {
//		val availableOpponents = opponents.filterAvailable(games)

		val opponents1 = opponents.filterNot { it.isPushed }
		val opponentLevels = opponents1/*availableOpponents*/.map { it.level }.distinct()

		val networks = games.networks(opponentLevels)

		val finishedNetworks = networks.filterFinished(opponents1/*availableOpponents*/)

		finishedNetworks.checkIfFinished(games)

		val startingOpponents: Map<Int, List<Opponent>> =
			opponents1
				.filter { a ->
					opponents1.none { b ->
						a.team == b.team && a.level > b.level
					}
				}
				.groupBy { it.level }

		println(startingOpponents)

		val teamCount = mutableMapOf(0 to opponents1.count { it.level == 0 })

		val symmetricalNetworkSizesAndCounts: MutableMap<Int, Pair<Int, Int>> = mutableMapOf()

		var level = 0
		while (true) {
			val pair = networks[level]?.let { networksOnLevel ->
				finishedNetworks[level]?.let { finishedNetworksOnLevel ->
					teamCount[level]!!
						.getSymmetricalNetworkSizesAndCounts(networksOnLevel, finishedNetworksOnLevel)
						.singleOrNull()
				}
			} ?: break

			symmetricalNetworkSizesAndCounts[level] = pair

			teamCount[level + 1] = pair.second + (startingOpponents[level + 1]?.size ?: 0)

			level++
		}

		println(symmetricalNetworkSizesAndCounts)
		println(teamCount)

		return symmetricalNetworkSizesAndCounts
			.map { (level, sizeAndCount) ->
				val (size, count) = sizeAndCount
				val gamesOnLevel = games.filter { it.level == level }
				val networksOnLevel: List<Pair<List<Opponent>, List<Game>>> =
					networks[level]!!.map { it to gamesOnLevel.connectedGames(it[0]) }
				val freeOpponentsOnLevel =
					opponents1.filter { it.level == level } -
						networksOnLevel.flatMap { it.first }
				Level(level, List(count) { matchIndex ->
					val network = networksOnLevel.getOrNull(matchIndex)
					when {
						network != null                   -> {
							val networkOpponents = network.first
							Match(List(size) { networkOpponents.getOrNull(it) }, network.second)
						}
						count == networksOnLevel.size + 1 -> {
							val teamOrNulls = List(size) { freeOpponentsOnLevel.getOrNull(it) }
							Match(teamOrNulls, teamOrNulls.filterNotNull().combinations.asGames(Game.State.PLANNED))
						}
						else                              -> {
							Match(List(size) { null }, emptyList())
						}
					}
				})
			}
	}

	/**
	 * @param maxAddTeams wie viele teams bekommen werden können theorethisch
	 * @param maxPushTeams wie viele teams hochgepusht werden können
	 */
	fun foo(
		maxAddTeams: Int,
		maxPushTeams: Int,
		currentLevel: Int,
		opponents: List<Opponent>,
		refs: Int?
	): List<Alternative> {
		val opponentCounts = opponents.groupBy { it.level }.mapValues { it.value.size }
		val (defaultMinLeftGames, defaultMinLeftTimeUnits) = minLeftGamesAndTimeUnits(0, opponentCounts, refs)
		val default = Alternative(0, defaultMinLeftGames, defaultMinLeftTimeUnits)

		val pushAlternatives = mutableListOf<Alternative>()
		for (difference in 1..maxPushTeams) {
			if (opponentCounts[currentLevel]!! - difference <= 1) continue

			val opponentsPerLevel = opponentCounts.toMutableMap()
			opponentsPerLevel[currentLevel] = opponentsPerLevel[currentLevel]!! - difference
			opponentsPerLevel[currentLevel + 1] = (opponentsPerLevel[currentLevel + 1] ?: 0) + difference

			val (leftGames, leftTimeUnits) = minLeftGamesAndTimeUnits(currentLevel, opponentsPerLevel, refs)
			val alternative = Alternative(-difference, leftGames, leftTimeUnits)
			pushAlternatives.add(alternative)
		}

		val addAlternatives = mutableListOf<Alternative>()
		for (difference in 1..maxAddTeams) {
			val opponentsPerLevel = opponentCounts.toMutableMap()
			opponentsPerLevel[currentLevel] = opponentsPerLevel[currentLevel]!! + difference

			val (leftGames, leftTimeUnits) = minLeftGamesAndTimeUnits(currentLevel, opponentsPerLevel, refs)
			val alternative = Alternative(difference, leftGames, leftTimeUnits)
			addAlternatives.add(alternative)
		}

		return pushAlternatives.filterIndexed { index, alternative ->
			alternative.leftTimeUnits < default.leftTimeUnits &&
				pushAlternatives.take(index).all { alternative.leftTimeUnits < it.leftTimeUnits }
		} + addAlternatives.filterIndexed { index, alternative ->
			alternative.leftTimeUnits < default.leftTimeUnits &&
				addAlternatives.take(index).all { alternative.leftTimeUnits < it.leftTimeUnits }
		}
	}

	data class Alternative(val difference: Int, val leftGames: Int, val leftTimeUnits: Int)

	fun minLeftGamesAndTimeUnits(currentLevel: Int, opponents: Map<Int, Int>, refs: Int?): Pair<Int, Int> {
		val opponentsOnCurrentLevel = opponents[currentLevel]!!
		if (opponentsOnCurrentLevel == 1) return 0 to 0
		val factors = opponentsOnCurrentLevel.factorPairs - (1 to opponentsOnCurrentLevel)
		return factors.map { (size, count) ->
			val nextLevel = currentLevel + 1
			val opponentsOnNextLevel = (opponents[nextLevel] ?: 0) + count
			val (minLeftGamesOfNextLevel, minLeftTimeUnitsOfNextLevel) = minLeftGamesAndTimeUnits(
				nextLevel,
				opponents + (nextLevel to opponentsOnNextLevel),
				refs
			)
			val games = (0 until size).toList().combinations
			val leftGamesOfCurrentLevel = count * games.size
			val leftTimeUnitsOfCurrentLevel = count *
				games
					.sequential(refs) { a, b ->
						a.first != b.first &&
							a.second != b.second &&
							a.first != b.second &&
							a.second != b.first
					}
					.size

			(leftGamesOfCurrentLevel +
				minLeftGamesOfNextLevel) to
				(leftTimeUnitsOfCurrentLevel +
					minLeftTimeUnitsOfNextLevel)
		}.minByOrNull { it.second }!!
	}

	private fun Map<Int, List<Pair<Int, Int>>>.filterValid(
		desiredLevelCount: Levels,
		currentLevel: Int
	) = mapValues { (_, it) ->
		it.filter { (_, count) ->
			count.primeFactors.size >= desiredLevelCount - 1 - currentLevel
		}
	}

	@JvmName("getSymmetricalNetworkSizesAndCountsIntInt")
	private fun Map<Int, Int>.getSymmetricalNetworkSizesAndCounts(
		biggestNetworkSize: Map<Int, Int?>,
		biggestFinishedNetworkSize: Map<Int, Int?>
	): Map<Int, List<Pair<Int, Int>>> =
		mapValues { (level, count) ->
			count.factorPairs.filter { (size, _) ->
				val n = biggestFinishedNetworkSize[level]
				size >= (biggestNetworkSize[level] ?: 2) &&
					if (n == null) true else size <= n
			}
		}

	private fun Map<Int, Int>.getSymmetricalNetworkSizesAndCounts(
		networks: Map<Int, List<List<Opponent>>>,
		finishedNetworks: Map<Int, List<List<Opponent>>>
	): Map<Int, List<Pair<Int, Int>>> {
		val biggestNetworkSize: Map<Int, Int?> =
			networks.mapValues { (_, networks) -> networks.maxOfOrNull { it.size } }

		val biggestFinishedNetworkSize: Map<Int, Int?> =
			finishedNetworks.mapValues { (_, networks) -> networks.maxOfOrNull { it.size } }
		return getSymmetricalNetworkSizesAndCounts(biggestNetworkSize, biggestFinishedNetworkSize)
	}

	private fun Int.getSymmetricalNetworkSizesAndCounts(
		biggestNetworkSize: Int?,
		biggestFinishedNetworkSize: Int?
	): List<Pair<Int, Int>> =
		this.factorPairs.filter { (size, _) ->
			size >= (biggestNetworkSize ?: 2) &&
				if (biggestFinishedNetworkSize == null) true
				else size <= biggestFinishedNetworkSize
		}

	private fun Int.getSymmetricalNetworkSizesAndCounts(
		networks: List<List<Opponent>>,
		finishedNetworks: List<List<Opponent>>
	): List<Pair<Int, Int>> {
		val biggestNetworkSize: Int? =
			networks.maxOfOrNull { it.size }

		val biggestFinishedNetworkSize: Int? =
			finishedNetworks.maxOfOrNull { it.size }

		return getSymmetricalNetworkSizesAndCounts(biggestNetworkSize, biggestFinishedNetworkSize)
	}

	private fun Map<Int, List<List<Opponent>>>.checkIfFinished(games: List<Game>) {
		forEach { (level, networks) ->
			check(networks.all { it.isNetworkFinished(games) })
			{ "There is a winner of an unfinished match on level $level." }
		}
	}

	private fun List<Game>.networks(opponentLevels: List<Int>): Map<Int, List<List<Opponent>>> =
		opponentLevels.associateWith { level ->
			networks.filter { it[0].level == level }
		}

	private fun List<Opponent>.isNetworkFinished(games: List<Game>) =
		games.containsAll(combinations.asGames(Game.State.FINISHED))

	private fun Map<Int, List<List<Opponent>>>.filterFinished(opponents: List<Opponent>) =
		mapValues { (level, networks) ->
			networks.filter { network ->
				val teams = network.map(Opponent::team)
				opponents.groupBy(Opponent::level)[level + 1]?.any { it.team in teams } == true
			}
		}

	private fun List<Opponent>.filterAvailable(games: List<Game>): List<Opponent> =
		filter { team ->
			games.none {
				it.state == Game.State.ACTIVE && (it.team1 == team || it.team2 == team)
			}
		}

	private fun List<Game>.areDirectlyConnected(team1: Opponent, team2: Opponent): Boolean =
		any { it.team1 == team1 && it.team2 == team2 || it.team2 == team1 && it.team1 == team2 }

	private fun List<Game>.directlyConnectedOpponents(team: Opponent): List<Opponent> =
		flatMap { listOf(it.team1, it.team2) }.filter { areDirectlyConnected(team, it) }

	private fun List<Game>.directlyConnectedGames(team: Opponent): List<Game> =
		filter { it.team1 == team || it.team2 == team }

//	private fun List<Game>.areConnected(team1: Opponent, team2: Opponent): Boolean =
//		areDirectlyConnected(team1, team2) ||
//			directlyConnectedOpponents(team1).any { (this - directlyConnectedGames(team1)).areConnected(it, team2) }

	private fun List<Game>.connectedOpponents(team: Opponent): List<Opponent> =
		directlyConnectedOpponents(team) +
			directlyConnectedOpponents(team).flatMap { (this - directlyConnectedGames(team)).connectedOpponents(it) }

	private fun List<Game>.connectedGames(team: Opponent): List<Game> =
		directlyConnectedGames(team) +
			directlyConnectedGames(team).flatMap {
				(this - directlyConnectedGames(team)).connectedGames(if (it.team1 == team) it.team2 else it.team1)
			}

	private val List<Game>.networks: List<List<Opponent>>
		get() =
			if (this.isEmpty())
				emptyList()
			else
				((this - connectedGames(first().team1)).networks +
					listOf(listOf(first().team1) + connectedOpponents(first().team1))).map { it.distinct() }
}

fun <E> List<E>.chunkedPermuted(chunkSize: Int, startIndex: Int = 0): List<List<E>> {
	val result = mutableListOf<List<E>>()
	var i = startIndex - 1
	while (++i < size) {
		if (chunkSize == 1) result.add(listOf(this[i]))
		else result.addAll(this.chunkedPermuted(chunkSize - 1, i + 1).map { listOf(this[i]) + it })
	}
	return result
}

private fun Iterable<Pair<Opponent, Opponent>>.asGames(state: Game.State): List<Game> =
	map { Game(state, it.first, it.second) }
