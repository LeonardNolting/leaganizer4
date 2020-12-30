package model.database

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.time.ZoneId

class ZoneIdColumnType : ColumnType() {
	override fun sqlType(): String = currentDialect.dataTypeProvider.textType()
	override fun valueFromDB(value: Any): ZoneId = when (value) {
		is ZoneId -> value
		is String -> ZoneId.of(value)
		else      -> error("Unexpected value of type ZoneId: $value of ${value::class.qualifiedName}")
	}

	override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) =
		super.setParameter(stmt, index, if (value is ZoneId) value.id else value)

	override fun notNullValueToDB(value: Any) =
		super.notNullValueToDB(if (value is ZoneId) value.id else value)
}

fun Table.zoneId(name: String): Column<ZoneId> = registerColumn(name, ZoneIdColumnType())