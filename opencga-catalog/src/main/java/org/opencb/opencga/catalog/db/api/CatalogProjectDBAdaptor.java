/*
 * Copyright 2015 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.opencga.catalog.db.api;

import org.opencb.commons.datastore.core.*;
import org.opencb.opencga.catalog.exceptions.CatalogDBException;
import org.opencb.opencga.catalog.models.AclEntry;
import org.opencb.opencga.catalog.models.Project;

import java.util.HashMap;
import java.util.Map;

import static org.opencb.commons.datastore.core.QueryParam.Type.*;

/**
 * Created by imedina on 08/01/16.
 */
public interface CatalogProjectDBAdaptor extends CatalogDBAdaptor<Project> {

    default boolean projectExists(long projectId) throws CatalogDBException {
        return count(new Query(QueryParams.ID.key(), projectId)).first() > 0;
    }

    default void checkProjectId(long projectId) throws CatalogDBException {
        if (projectId < 0) {
            throw CatalogDBException.newInstance("Project id '{}' is not valid: ", projectId);
        }

        if (!projectExists(projectId)) {
            throw CatalogDBException.newInstance("Project id '{}' does not exist", projectId);
        }
    }

    QueryResult<Project> createProject(String userId, Project project, QueryOptions options) throws CatalogDBException;

    QueryResult<Project> getAllProjects(String userId, QueryOptions options) throws CatalogDBException;

    QueryResult<Project> getProject(long project, QueryOptions options) throws CatalogDBException;

//    @Deprecated
//    default QueryResult<Project> deleteProject(long projectId) throws CatalogDBException {
//        return delete(projectId, false);
//    }

    QueryResult renameProjectAlias(long projectId, String newProjectName) throws CatalogDBException;

//    @Deprecated
//    QueryResult<Project> modifyProject(long projectId, ObjectMap parameters) throws CatalogDBException;

    long getProjectId(String userId, String projectAlias) throws CatalogDBException;

    String getProjectOwnerId(long projectId) throws CatalogDBException;

    QueryResult<AclEntry> getProjectAcl(long projectId, String userId) throws CatalogDBException;

    QueryResult setProjectAcl(long projectId, AclEntry newAcl) throws CatalogDBException;

    enum QueryParams implements QueryParam {
        ID("id", INTEGER_ARRAY, ""),
        NAME("name", TEXT_ARRAY, ""),
        ALIAS("alias", TEXT_ARRAY, ""),
        CREATION_DATE("creationDate", TEXT_ARRAY, ""),
        DESCRIPTION("description", TEXT_ARRAY, ""),
        ORGANIZATION("organization", TEXT_ARRAY, ""),
        STATUS_NAME("status.name", TEXT, ""),
        STATUS_MSG("status.msg", TEXT, ""),
        STATUS_DATE("status.date", TEXT, ""),
        LAST_MODIFIED("lastModified", TEXT_ARRAY, ""),
        DISK_USAGE("diskUsage", INTEGER, ""),
        USER_ID("userId", TEXT, ""),
        ATTRIBUTES("attributes", TEXT, ""), // "Format: <key><operation><stringValue> where <operation> is [<|<=|>|>=|==|!=|~|!~]"
        NATTRIBUTES("nattributes", DECIMAL, ""), // "Format: <key><operation><numericalValue> where <operation> is [<|<=|>|>=|==|!=|~|!~]"
        BATTRIBUTES("battributes", BOOLEAN, ""), // "Format: <key><operation><true|false> where <operation> is [==|!=]"

        STUDY_ID("study.id", INTEGER_ARRAY, ""),
        STUDY_NAME("study.name", TEXT_ARRAY, ""),
        STUDY_ALIAS("study.alias", TEXT_ARRAY, ""),
        STUDY_CREATOR_ID("study.creatorId", TEXT_ARRAY, ""),
        STUDY_STATUS("study.status", TEXT_ARRAY, ""),
        STUDY_LAST_MODIFIED("study.lastModified", TEXT_ARRAY, ""),

        // TOCHECK: Pedro. Check parameter user_others_id.
        ACL_USER_ID("acl.userId", TEXT_ARRAY, ""),
        ACL_READ("acl.read", BOOLEAN, ""),
        ACL_WRITE("acl.write", BOOLEAN, ""),
        ACL_EXECUTE("acl.execute", BOOLEAN, ""),
        ACL_DELETE("acl.delete", BOOLEAN, "");

        private static Map<String, QueryParams> map = new HashMap<>();
        static {
            for (QueryParams params : QueryParams.values()) {
                map.put(params.key(), params);
            }
        }

        private final String key;
        private Type type;
        private String description;

        QueryParams(String key, Type type, String description) {
            this.key = key;
            this.type = type;
            this.description = description;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public Type type() {
            return type;
        }

        @Override
        public String description() {
            return description;
        }

        public static Map<String, QueryParams> getMap() {
            return map;
        }

        public static QueryParams getParam(String key) {
            return map.get(key);
        }
    }

}
