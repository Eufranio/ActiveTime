package com.mcsimonflash.sponge.activetime.commands.elements;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.ArgumentParseException;
import org.spongepowered.api.command.args.CommandArgs;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.CommandElement;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.text.Text;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class FlagsElement extends CommandElement {

    private final ImmutableMap<List<String>, CommandElement> flags;
    private final ImmutableMap<String, CommandElement> aliases;

    public FlagsElement(Map<List<String>, CommandElement> flags) {
        super(null);
        this.flags = ImmutableMap.copyOf(flags);
        ImmutableMap.Builder<String, CommandElement> builder = ImmutableMap.builder();
        flags.forEach((k, v) -> k.forEach(f -> builder.put("-" + f.toLowerCase(), v)));
        aliases = builder.build();
    }

    @Override
    public void parse(CommandSource src, CommandArgs args, CommandContext ctx) throws ArgumentParseException {
        while (args.hasNext() && args.peek().startsWith("-")) {
            String[] split = args.next().split("=", 2);
            CommandElement element = aliases.get(split[0]);
            if (element == null) {
                throw args.createError(Text.of("Unknown flag ", split[0], "."));
            } else if (split.length == 2) {
                args.insertArg(split[1]);
            }
            try {
                element.parse(src, args, ctx);
            } catch (ArgumentParseException e) {
                throw args.createError(Text.of("Error parsing value for flag ", split[0], ": ", e.getText()));
            }
        }
    }

    @Override
    public List<String> complete(CommandSource src, CommandArgs args, CommandContext ctx) {
        List<List<String>> unused = Lists.newArrayList(flags.keySet());
        try {
            while (args.hasNext() && args.peek().startsWith("-")) {
                String[] split = args.next().split("=", 2);
                CommandElement element = aliases.get(split[0].toLowerCase());
                if (element == null || split.length == 1 && !args.hasNext()) {
                    return unused.stream().flatMap(Collection::stream).map(s -> "-" + s).filter(s -> s.toLowerCase().startsWith(split[0].toLowerCase())).collect(Collectors.toList());
                } else if (split.length == 2) {
                    args.insertArg(split[1]); //why is this needed?
                }
                Object state = args.getState();
                try {
                    element.parse(src, args, ctx);
                    if (args.hasNext()) {
                        unused.removeIf(l -> l.contains(split[0].substring(1).toLowerCase()));
                        continue;
                    }
                } catch (ArgumentParseException ignored) {}
                args.setState(state);
                String prefix = split.length == 2 ? split[0] + "=" : args.hasNext() ? "" : split[0] + " ";
                return element.complete(src, args, ctx).stream().map(s -> prefix + s).collect(Collectors.toList());
            }
        } catch (ArgumentParseException ignored) {}
        return args.nextIfPresent().map(String::toLowerCase).map(a -> unused.stream()
                .flatMap(Collection::stream).map(s -> "-" + s).filter(s -> s.toLowerCase().startsWith(a)).collect(Collectors.toList()))
                .orElse(ImmutableList.of());
    }

    @Override
    public Text getUsage(CommandSource src) {
        List<Object> args = Lists.newArrayList();
        for (Map.Entry<List<String>, CommandElement> entry : flags.entrySet()) {
            args.add("[" + String.join("|", entry.getKey()));
            Text usage = entry.getValue().getUsage(src);
            if (!usage.isEmpty()) {
                args.add(": ");
                args.add(usage);
            }
            args.add("] ");
        }
        return Text.of(args.toArray());
    }

    @Override
    @Deprecated
    protected Object parseValue(CommandSource source, CommandArgs args) throws ArgumentParseException {
        throw new UnsupportedOperationException("Attempted to parse a value from flags.");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Map<List<String>, CommandElement> flags = Maps.newHashMap();

        public Builder flag(CommandElement element, String... flags) {
            this.flags.put(Arrays.stream(flags).map(String::toLowerCase).collect(Collectors.toList()), element);
            return this;
        }

        public Builder flag(String... flags) {
            return flags.length != 0 ? flag(new MarkTrue(flags[0]), flags) : this;
        }

        public FlagsElement build() {
            return new FlagsElement(flags);
        }

    }

    public static class MarkTrue extends CommandElement {

        public MarkTrue(String flag) {
            super(Text.of(flag));
        }

        @Override
        protected Object parseValue(CommandSource source, CommandArgs args) throws ArgumentParseException {
            return Boolean.TRUE;
        }

        @Override
        public List<String> complete(CommandSource src, CommandArgs args, CommandContext context) {
            return ImmutableList.of();
        }

        @Override
        public Text getUsage(CommandSource src) {
            return Text.EMPTY;
        }

    }

}