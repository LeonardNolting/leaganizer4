package apiResult

interface MojangApiResult : ApiResult {
	class UID : java.util.ArrayList<UID.Entry>(), MojangApiResult {
		data class Entry(
			val name: String,
			val changedToAt: String
		)
	}

	/*class UID(
		val name: String,
		val changedToAt: Int?
	) : MojangApiResult()*/
	data class IGN(
		val id: String
	) : MojangApiResult
}