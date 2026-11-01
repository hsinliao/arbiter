package io.github.arbiter.command.result;

public record LocalizedArgument(String messageKey, Object[] args) {
    public LocalizedArgument {
        args = args == null ? new Object[0] : args.clone();
    }

    @Override
    public Object[] args() {
        return args.clone();
    }
}
