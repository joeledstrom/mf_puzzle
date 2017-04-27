import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.flatMap
import com.github.kittinunf.result.map
import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName



data class StockQuote(
    @SerializedName("quote_date") val date: String,
    val paper: String,
    @SerializedName("exch") val exchange: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double
)


// Note. Any exceptions thrown by the JSON parsing will be wrapped
// inside the returned result instead of it having a usable value
fun parseQuotesFromJson(data: String): Result<List<StockQuote>, Exception> = Result.of {
    val obj = JsonParser().parse(data).asJsonObject
    Gson().fromJson<List<StockQuote>>(obj["data"])
}


// Note. Assumes that the list of quotes are ordered by date descending
// which is currently the order that the API call returns its data in.
fun calculateHighestReturn(quotes: List<StockQuote>): Pair<StockQuote, StockQuote> {

    var highestReturn = 0.0
    var highestSell = quotes.first()
    var bestBuySellTuple = Pair(quotes.first(), quotes.first())


    val tail = quotes.asSequence().drop(1)

    for (current in tail) {
        // keep track of the quote with the highest high so far
        if (current.high > highestSell.high)
            highestSell = current

        val currentReturn = highestSell.high - current.low

        // keep track of the highest return so far
        // and update the best buy/sell tuple whenever we find a higher return
        if (currentReturn > highestReturn) {
            bestBuySellTuple = Pair(current, highestSell)
            highestReturn = currentReturn
        }
    }

    return bestBuySellTuple
}

fun main(args: Array<String>) {

    // A User-Agent seems to be required by the web server
    // otherwise the http GET yields 403 Forbidden
    FuelManager.instance.baseHeaders = mapOf("User-Agent" to "mf_puzzle/1.0")


    val url = "http://www.modularfinance.se/api/puzzles/index-trader.json"

    // perform HTTP GET request
    // Note. body is of type Result<String, FuelError> which means either its the content of
    // the body as a string or its an Exception of type FuelError
    val (_, _, body) = url.httpGet().responseString()

    // map/flatMap on the Result type takes care of error handling, the first exception
    // will abort the "rest of the chain"
    // if for example httpGet() failed due to network/IO errors:
    //      parseQuotesFromJson and calculateHighestReturn will never be called
    val result = body
            .flatMap(::parseQuotesFromJson)
            .map(::calculateHighestReturn)


    result.fold(
            { (buy, sell) ->
                println("Buy at date: " + buy.date + " for: " + buy.low)
                println("Sell at date: " + sell.date + " for: " + sell.high)
                println("Absolute return: " + (sell.high - buy.low))
            },
            { e ->
                println("Got an exception during processing: " + e)
            }
    )
}