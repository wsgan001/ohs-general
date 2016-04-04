package ohs.eden.keyphrase;

public class KPPath {

	public static final String DATA_DIR = "../../data/entity_iden/";

	public static final String TEXT_DUMP_DIR = DATA_DIR + "db_dump/";

	public static final String PAPER_DUMP_FILE = TEXT_DUMP_DIR + "paper.csv";

	public static final String PATENT_DUMP_FILE = TEXT_DUMP_DIR + "patent.csv";

	public static final String REPORT_DUMP_FILE = TEXT_DUMP_DIR + "report.csv";

	public static final String SINGLE_DUMP_FILE = TEXT_DUMP_DIR + "3p.csv.gz";

	public static final String SINGLE_DUMP_POS_FILE = TEXT_DUMP_DIR + "3p_pos.csv.gz";

	public static final String KEYPHRASE_DIR = DATA_DIR + "keyphrase/";

	public static final String KEYWORD_DATA_FILE = KEYPHRASE_DIR + "keyword_data.txt.gz";

	public static final String KEYWORD_ABBR_FILE = KEYPHRASE_DIR + "keyword_abbr.txt.gz";

	public static final String KEYWORD_CLUSTER_FILE = KEYPHRASE_DIR + "keyword_cluster.txt";

	public static final String KEYWORD_POS_CNT_FILE = KEYPHRASE_DIR + "kwd_pos_seq_cnt.txt";

	public static final String PATENT_KEYWORD_MATCH_FILE = KEYPHRASE_DIR + "patent_kwd_match.txt";

	public static final String VOCAB_FILE = KEYPHRASE_DIR + "vocab.ser.gz";

}
