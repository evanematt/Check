package com.lewandivka.block;

import com.lewandivka.core.registry.PropSpec;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/** Builds the real block-state properties from the catalog description (the blockstate files come from the same spec). */
final class BlockProps {

    private BlockProps() {
    }

    static Property<?> create(PropSpec p) {
        return switch (p.type()) {
            case BOOL -> BooleanProperty.of(p.name());
            // named values are stored as their index: the generated blockstate files use the same numbers
            case ENUM -> IntProperty.of(p.name(), 0, p.count() - 1);
            case INT -> IntProperty.of(p.name(), p.min(), p.max());
            case FACING -> {
                List<Direction> dirs = new ArrayList<>();
                for (String v : p.values()) {
                    Direction d = Direction.byName(v);
                    if (d == null) {
                        throw new IllegalStateException("bad direction " + v);
                    }
                    dirs.add(d);
                }
                yield DirectionProperty.of(p.name(), dirs);
            }
        };
    }
}
