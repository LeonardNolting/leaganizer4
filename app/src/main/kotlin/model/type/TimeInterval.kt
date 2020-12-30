package model.type

import model.database.IntegerWrapperColumnType
import model.database.WrapperColumnType
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

// https://youtrack.jetbrains.com/issue/KT-34024
data class TimeInterval(override val value: Int) : WrapperColumnType.Wrapper

class TimeIntervalColumnType : IntegerWrapperColumnType<TimeInterval>(TimeInterval::class)

fun Table.timeInterval(name: String): Column<TimeInterval> = registerColumn(name, TimeIntervalColumnType())