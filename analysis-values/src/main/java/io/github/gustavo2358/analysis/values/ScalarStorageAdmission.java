package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.Memory;
import io.github.gustavo2358.air.model.Types;

/** The same necessary storage guard for declarations and individual demands.
 * A declaration's precision/coverage changes openness, not this capability. */
final class ScalarStorageAdmission {
    private ScalarStorageAdmission() { }
    static Memory.Cell cell(Memory.ObjectDeclaration object,Memory.Storage storage) {
        if(object==null||!(object.storage() instanceof Memory.CellBinding)
            ||!(storage instanceof Memory.Cell cell)||!domain(object.typeRef())||!domain(cell.typeRef()))return null;
        return cell;
    }
    private static boolean domain(Types.TypeRef type) {
        return type instanceof Types.Known known&&(known.type()==Types.Builtin.TEXT||known.type()==Types.Builtin.INT);
    }
}
