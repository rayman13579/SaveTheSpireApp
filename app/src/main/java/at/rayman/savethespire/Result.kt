package at.rayman.savethespire


data class Result(val success: Boolean, var value: String) {

    companion object {
        fun success(value: String): Result {
            return Result(true, value)
        }

        fun error(value: String): Result {
            return Result(false, value)
        }

    }

}