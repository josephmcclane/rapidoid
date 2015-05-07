package org.rapidoidx.db;

/*
 * #%L
 * rapidoid-x-db-tests
 * %%
 * Copyright (C) 2014 - 2015 Nikolche Mihajlovski
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.rapidoid.annotation.Authors;
import org.rapidoid.annotation.Since;
import org.rapidoid.config.Conf;
import org.rapidoid.lambda.Operation;
import org.rapidoid.log.Log;
import org.rapidoid.log.LogLevel;
import org.rapidoid.util.U;
import org.rapidoidx.db.DB;
import org.rapidoidx.db.model.Person;
import org.testng.annotations.Test;

@Authors("Nikolche Mihajlovski")
@Since("3.0.0")
public class DbStatisticalTest extends DbTestCommons {

	class Op {
		final int op = rnd(3);
		final String name = rndStr(0, 100);
		final int age = rnd();
		final long id = rnd((int) (DB.size() * 2) + 10);
		final Person person = new Person(name, age);
		final boolean fail = rnd(30) == 0;
	}

	class Ret {
		long id = -1;
		boolean illegalId = false;
		boolean ok = false;
	}

	private final Map<Object, Object> persons = Collections.synchronizedMap(U.map());

	@Test
	public void testDbOperations() {

		Log.setLogLevel(LogLevel.SEVERE);

		multiThreaded(Conf.cpus(), 50000, new Runnable() {
			@Override
			public synchronized void run() {

				int n = rnd(10) + 1;
				final Op[] ops = new Op[n];
				final Ret[] rets = new Ret[n];

				for (int i = 0; i < ops.length; i++) {
					ops[i] = new Op();
					rets[i] = new Ret();
				}

				if (yesNo()) {

					final AtomicBoolean complete = new AtomicBoolean(false);

					DB.transaction(new Runnable() {
						@Override
						public void run() {
							for (int i = 0; i < ops.length; i++) {

								try {
									doDbOp(ops[i], rets[i]);
								} catch (IllegalArgumentException e) {
									rets[i].illegalId = true;
									throw U.rte(e);
								}

								if (ops[i].fail) {
									throw U.rte("err");
								}
							}

							complete.set(true);
						}
					}, false, null);

					for (int i = 0; i < ops.length; i++) {
						if (ops[i].fail || !rets[i].ok || rets[i].illegalId) {
							return;
						}
					}

					U.must(complete.get());

					for (int i = 0; i < ops.length; i++) {
						doShadowOp(ops[i], rets[i]);
					}

				} else {
					try {
						doDbOp(ops[0], rets[0]);
					} catch (IllegalArgumentException e) {
						rets[0].illegalId = true;
					}

					doShadowOp(ops[0], rets[0]);
				}
			}
		});

		DB.shutdown();

		System.out.println("Comparing data...");
		compareData();
		System.out.println("Total " + persons.size() + " records.");

	}

	private void compareData() {
		eq(DB.size(), persons.size());
		DB.each(new Operation<Person>() {
			@Override
			public void execute(Person p) throws Exception {
				Person p2 = (Person) persons.get(p.id());
				eq(p2.id(), p.id());
				eq(p2.age, p.age);
				eq(p2.name, p.name);
			}
		});
	}

	private void doDbOp(Op op, Ret ret) {
		switch (op.op) {
		case 0:
			ret.id = DB.insert(op.person);
			break;

		case 1:
			DB.delete(op.id);
			break;

		case 2:
			op.person.version(DB.getVersionOf(op.id));
			DB.update(op.id, op.person);
			break;

		default:
			throw U.notExpected();
		}
		ret.ok = true;
	}

	private void doShadowOp(Op op, Ret ret) {
		switch (op.op) {
		case 0:
			assert persons.put(ret.id, op.person) == null;
			break;

		case 1:
			if (!ret.illegalId) {
				assert persons.remove(op.id) != null;
			} else {
				assert !persons.containsKey(op.id);
			}
			break;

		case 2:
			if (!ret.illegalId) {
				assert persons.put(op.id, op.person) != null;
			} else {
				assert !persons.containsKey(op.id);
			}
			break;

		default:
			throw U.notExpected();
		}
	}

}