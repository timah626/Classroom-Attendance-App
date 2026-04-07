
sealed class NetworkState {
    object Loading : NetworkState()
    data class Success(val data: String) : NetworkState()
    data class Error(val message: String) : NetworkState()
}

// handling different network states
fun handleState(state: NetworkState) {
    when (state) {
        is NetworkState.Loading -> println("Loading...")
        is NetworkState.Success -> println("Success: ${state.data}")
        is NetworkState.Error -> println("Error: ${state.message}")
    }
}


fun main() {
    val states = listOf(
        NetworkState.Loading,
        NetworkState.Success("User data loaded"),
        NetworkState.Error("Network timeout")
    )
    
    states.forEach { handleState(it) }
}
