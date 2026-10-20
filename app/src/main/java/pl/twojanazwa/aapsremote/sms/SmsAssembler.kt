package pl.twojanazwa.aapsremote.sms

object SmsAssembler {
    /** SMS-03: grupy wg równości nadawcy (null osobno), kolejność pierwszego wystąpienia; body null = "". */
    fun assemble(parts: List<RawSmsPart>): List<AssembledSms> {
        val groups = LinkedHashMap<String?, MutableList<RawSmsPart>>()
        for (part in parts) {
            groups.getOrPut(part.sender) { mutableListOf() }.add(part)
        }
        return groups.map { (sender, group) ->
            AssembledSms(
                sender = sender,
                body = group.joinToString("") { it.body ?: "" },
                serviceCenterTimestampMillis = group.first().serviceCenterTimestampMillis,
            )
        }
    }
}
