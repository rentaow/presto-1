/*
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
package io.prestosql.tests.jdbc;

import io.prestosql.tempto.Requires;
import io.prestosql.tempto.fulfillment.table.hive.tpch.ImmutableTpchTablesRequirements.ImmutableNationTable;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static io.prestosql.tempto.assertions.QueryAssert.assertThat;
import static io.prestosql.tests.ImmutableLdapObjectDefinitions.CHILD_GROUP_USER;
import static io.prestosql.tests.ImmutableLdapObjectDefinitions.ORPHAN_USER;
import static io.prestosql.tests.ImmutableLdapObjectDefinitions.PARENT_GROUP_USER;
import static io.prestosql.tests.TestGroups.LDAP;
import static io.prestosql.tests.TestGroups.PRESTO_JDBC;
import static io.prestosql.tests.TestGroups.PROFILE_SPECIFIC_TESTS;
import static io.prestosql.tests.TpchTableResults.PRESTO_NATION_RESULT;
import static java.lang.String.format;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestLdapPrestoJdbc
        extends BaseLdapJdbcTest
{
    @Override
    protected String getLdapUrlFormat()
    {
        return "jdbc:presto://%s?SSL=true&SSLTrustStorePath=%s&SSLTrustStorePassword=%s";
    }

    @Requires(ImmutableNationTable.class)
    @Test(groups = {LDAP, PRESTO_JDBC, PROFILE_SPECIFIC_TESTS}, timeOut = TIMEOUT)
    public void shouldRunQueryWithLdap()
            throws SQLException
    {
        assertThat(executeLdapQuery(NATION_SELECT_ALL_QUERY, ldapUserName, ldapUserPassword)).matches(PRESTO_NATION_RESULT);
    }

    @Test(groups = {LDAP, PRESTO_JDBC, PROFILE_SPECIFIC_TESTS}, timeOut = TIMEOUT)
    public void shouldFailQueryForLdapUserInChildGroup()
    {
        String name = CHILD_GROUP_USER.getAttributes().get("cn");
        expectQueryToFailForUserNotInGroup(name);
    }

    @Test(groups = {LDAP, PRESTO_JDBC, PROFILE_SPECIFIC_TESTS}, timeOut = TIMEOUT)
    public void shouldFailQueryForLdapUserInParentGroup()
    {
        String name = PARENT_GROUP_USER.getAttributes().get("cn");
        expectQueryToFailForUserNotInGroup(name);
    }

    @Test(groups = {LDAP, PRESTO_JDBC, PROFILE_SPECIFIC_TESTS}, timeOut = TIMEOUT)
    public void shouldFailQueryForOrphanLdapUser()
    {
        String name = ORPHAN_USER.getAttributes().get("cn");
        expectQueryToFailForUserNotInGroup(name);
    }

    @Test(groups = {LDAP, PRESTO_JDBC, PROFILE_SPECIFIC_TESTS}, timeOut = TIMEOUT)
    public void shouldFailQueryForWrongLdapPassword()
    {
        expectQueryToFail(ldapUserName, "wrong_password", "Authentication failed: Access Denied: Invalid credentials");
    }

    @Test(groups = {LDAP, PRESTO_JDBC, PROFILE_SPECIFIC_TESTS}, timeOut = TIMEOUT)
    public void shouldFailQueryForWrongLdapUser()
    {
        assertThatThrownBy(() -> executeLdapQuery(NATION_SELECT_ALL_QUERY, "invalid_user", ldapUserPassword))
                .isInstanceOf(SQLException.class)
                .hasMessageStartingWith("Authentication failed");
    }

    @Test(groups = {LDAP, PRESTO_JDBC, PROFILE_SPECIFIC_TESTS}, timeOut = TIMEOUT)
    public void shouldFailQueryForEmptyUser()
    {
        expectQueryToFail("", ldapUserPassword, "Connection property 'user' value is empty");
    }

    @Test(groups = {LDAP, PRESTO_JDBC, PROFILE_SPECIFIC_TESTS}, timeOut = TIMEOUT)
    public void shouldFailQueryForLdapWithoutPassword()
    {
        expectQueryToFail(ldapUserName, null, "Authentication failed: Unauthorized");
    }

    @Test(groups = {LDAP, PRESTO_JDBC, PROFILE_SPECIFIC_TESTS}, timeOut = TIMEOUT)
    public void shouldFailQueryForLdapWithoutSsl()
    {
        try {
            DriverManager.getConnection("jdbc:presto://" + prestoServer(), ldapUserName, ldapUserPassword);
            fail();
        }
        catch (SQLException exception) {
            assertEquals(exception.getMessage(), "Authentication using username/password requires SSL to be enabled");
        }
    }

    @Test(groups = {LDAP, PRESTO_JDBC, PROFILE_SPECIFIC_TESTS}, timeOut = TIMEOUT)
    public void shouldFailForIncorrectTrustStore()
    {
        try {
            String url = format("jdbc:presto://%s?SSL=true&SSLTrustStorePath=%s&SSLTrustStorePassword=%s", prestoServer(), ldapTruststorePath, "wrong_password");
            Connection connection = DriverManager.getConnection(url, ldapUserName, ldapUserPassword);
            Statement statement = connection.createStatement();
            statement.executeQuery(NATION_SELECT_ALL_QUERY);
            fail();
        }
        catch (SQLException exception) {
            assertEquals(exception.getMessage(), "Error setting up SSL: Keystore was tampered with, or password was incorrect");
        }
    }

    @Test(groups = {LDAP, PRESTO_JDBC, PROFILE_SPECIFIC_TESTS}, timeOut = TIMEOUT)
    public void shouldFailForUserWithColon()
    {
        expectQueryToFail("UserWith:Colon", ldapUserPassword, "Illegal character ':' found in username");
    }

    private void expectQueryToFailForUserNotInGroup(String user)
    {
        expectQueryToFail(user, ldapUserPassword, format("Authentication failed: Access Denied: User [%s] not a member of an authorized group", user));
    }
}
