package net.sanaechan.web.result;

public interface LogicResult {

    boolean isSuccess();

    public static LogicResult fail() {
        return Failed.INSTANCE;
    }

    public static LogicResult success() {
        return Success.INSTANCE;
    }

    class Failed implements LogicResult {

        public static final LogicResult INSTANCE = new Failed();

        @Override
        public boolean isSuccess() {
            return false;
        }
    }

    class Success implements LogicResult {

        public static final LogicResult INSTANCE = new Success();

        @Override
        public boolean isSuccess() {
            return true;
        }
    }
}
