package ohs.nlp.ling.types;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import ohs.io.FileUtils;

public class KCollection extends ArrayList<KDocument> {

	/**
	 * 
	 */
	private static final long serialVersionUID = -8797495521485834143L;

	public void read(ObjectInputStream ois) throws Exception {
		int size = ois.readInt();

		for (int i = 0; i < size; i++) {
			KDocument doc = new KDocument();
			doc.read(ois);
			add(doc);
		}
	}

	public void read(String fileName) throws Exception {
		ObjectInputStream ois = FileUtils.openObjectInputStream(fileName);
		read(ois);
		ois.close();
	}

	public void write(ObjectOutputStream oos) throws Exception {
		oos.writeInt(this.size());

		for (KDocument doc : this) {
			doc.write(oos);
		}
	}

	public void write(String fileName) throws Exception {
		ObjectOutputStream oos = FileUtils.openObjectOutputStream(fileName);
		write(oos);
		oos.close();
	}
}
