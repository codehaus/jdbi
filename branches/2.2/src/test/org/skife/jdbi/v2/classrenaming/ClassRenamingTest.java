package org.skife.jdbi.v2.classrenaming;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import junit.framework.TestCase;

import org.antlr.stringtemplate.StringTemplateGroupLoader;
import org.skife.jdbi.derby.Tools;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.Query;
import org.skife.jdbi.v2.tweak.ConnectionFactory;
import org.skife.jdbi.v2.tweak.StatementLocator;
import org.skife.jdbi.v2.unstable.stringtemplate.ClasspathGroupLoader;
import org.skife.jdbi.v2.unstable.stringtemplate.StringTemplateStatementLocator;

public class ClassRenamingTest extends TestCase {

    public void setUp() throws Exception
    {
        Tools.start();
        Tools.dropAndCreateSomething();

        final Connection conn = Tools.getConnection();
        final Statement stmt = conn.createStatement();

		stmt.execute("insert into foo (bar, baz, blo) VALUES ('A', 'B', 20)");
		stmt.execute("insert into foo (bar, baz, blo) VALUES ('c', 'f', 30)");
		stmt.execute("insert into foo (bar, baz, blo) VALUES ('d', 'g', 40)");
		stmt.execute("insert into foo (bar, baz, blo) VALUES ('e', 'h', 50)");

		stmt.close();
    }

    public void tearDown() throws Exception
    {
        Tools.stop();
    }

	public void testRenamedClasses() throws Exception {

		final DBI dbi = new DBI(new ConnectionFactory()
        {
            public Connection openConnection() throws SQLException
            {
                return Tools.getConnection();
            }
        });

		StringTemplateGroupLoader loader = new ClasspathGroupLoader(ClassRenamingTest.class.getPackage().getName().replace('.', '/'));
		StatementLocator locator = new StringTemplateStatementLocator(loader);

		dbi.setStatementLocator(locator);
		Handle handle = dbi.open();
		Query<ClassRenamingBean> query = handle.createQuery("classrenaming:brianquery").map(ClassRenamingBean.class);
		List<ClassRenamingBean> rs = query.list();

		assertEquals(4, rs.size());

		for (ClassRenamingBean bean: rs) {
			System.out.println(bean);
		}
	}

}
