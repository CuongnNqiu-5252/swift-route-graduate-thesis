import psycopg2

conn = psycopg2.connect(host='localhost', port=5432, user='openpg', password='openpgpwd', dbname='driver_db')
cur = conn.cursor()
cur.execute("SELECT column_name FROM information_schema.columns WHERE table_name='outbox_events';")
print('outbox_events cols:', [r[0] for r in cur.fetchall()])
cur.execute("SELECT column_name FROM information_schema.columns WHERE table_name='drivers';")
print('drivers cols:', [r[0] for r in cur.fetchall()])
conn.close()
