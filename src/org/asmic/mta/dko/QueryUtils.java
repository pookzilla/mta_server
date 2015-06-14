package org.asmic.mta.dko;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.kered.dko.Field;
import org.kered.dko.Query;
import org.kered.dko.Table;

import com.google.common.base.Function;

/**
 * Tools for manipulating DKO queries.
 * 
 * @author kim
 */
public final class QueryUtils  {

	/**
	 * Return the results of a query as a list of maps (including only provided fields.)
	 * 
	 * @param query source query
	 * @param nameTransformer optional function that will map from provided fields to map key names
	 * @param fields the list of fields to include in each map
	 * @return the list of maps
	 */
	public static List<Map<String, Object>> asListOfMaps(Query<? extends Table> query, Function<Field<?>, String> nameTransformer, Field<?>... fields) {
		String[] fieldNames = new String[fields.length];
		for (int i = 0; i < fields.length; i++) {
			fieldNames[i] = nameTransformer != null ? nameTransformer.apply(fields[i]) : fields[i].NAME;
		}
		
		List<? extends Table> allResults = query.asList();
		List<Map<String, Object>> results = new ArrayList<>(allResults.size());
		for (Table element : allResults) {
			Map<String, Object> row = new HashMap<>(fields.length);
			for (int i = 0; i < fields.length; i++) {
				row.put (fieldNames[i], element.get(fields[i]));
			}
			results.add(row);
		}
		return results;
	}
}
