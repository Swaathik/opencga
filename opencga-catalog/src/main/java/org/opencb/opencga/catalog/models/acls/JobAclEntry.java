package org.opencb.opencga.catalog.models.acls;

import org.opencb.commons.datastore.core.ObjectMap;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by pfurio on 11/05/16.
 */
public class JobAclEntry extends AbstractAclEntry<JobAclEntry.JobPermissions> {

    public enum JobPermissions {
        VIEW,
        UPDATE,
        DELETE,
        SHARE
    }

    public JobAclEntry() {
        this("", Collections.emptyList());
    }

    public JobAclEntry(String member, EnumSet<JobPermissions> permissions) {
        super(member, permissions);
    }

    public JobAclEntry(String member, ObjectMap permissions) {
        super(member, EnumSet.noneOf(JobPermissions.class));

        EnumSet<JobPermissions> aux = EnumSet.allOf(JobPermissions.class);
        for (JobPermissions permission : aux) {
            if (permissions.containsKey(permission.name()) && permissions.getBoolean(permission.name())) {
                this.permissions.add(permission);
            }
        }
    }

    public JobAclEntry(String member, List<String> permissions) {
        super(member, EnumSet.noneOf(JobPermissions.class));
        if (permissions.size() > 0) {
            this.permissions.addAll(permissions.stream().map(JobPermissions::valueOf).collect(Collectors.toList()));
        }
    }

}
