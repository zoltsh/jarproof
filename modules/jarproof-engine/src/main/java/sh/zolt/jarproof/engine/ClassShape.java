package sh.zolt.jarproof.engine;

import java.util.List;
import java.util.Optional;

/** Declared shape of one class: identity, hierarchy, nest attributes, and members. */
record ClassShape(
        String internalName,
        int accessFlags,
        Optional<String> superInternalName,
        List<String> interfaceInternalNames,
        Optional<String> nestHostInternalName,
        List<String> nestMemberInternalNames,
        List<MemberShape> members) {
    ClassShape {
        interfaceInternalNames = List.copyOf(interfaceInternalNames);
        nestMemberInternalNames = List.copyOf(nestMemberInternalNames);
        members = List.copyOf(members);
    }
}
