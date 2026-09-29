package com.example.ecommerce.util;

import java.util.function.Consumer;
import java.util.function.Function;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;

/** Owns the single SessionFactory and offers small helpers for transactions. */
public final class HibernateUtil {

    private static SessionFactory sessionFactory;

    private HibernateUtil() {}

    public static synchronized SessionFactory getSessionFactory() {
        if (sessionFactory == null || sessionFactory.isClosed()) {
            sessionFactory = new Configuration().configure("hibernate.cfg.xml").buildSessionFactory();
        }
        return sessionFactory;
    }

    /** Runs work in a transaction: commit on success, rollback on any RuntimeException. */
    public static <T> T inTransaction(Function<Session, T> work) {
        try (Session session = getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                T result = work.apply(session);
                tx.commit();
                return result;
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }
        }
    }

    public static void runInTransaction(Consumer<Session> work) {
        inTransaction(s -> { work.accept(s); return null; });
    }

    /** Read-only helper (no explicit transaction). */
    public static <T> T query(Function<Session, T> work) {
        try (Session session = getSessionFactory().openSession()) {
            return work.apply(session);
        }
    }

    public static synchronized void shutdown() {
        if (sessionFactory != null && !sessionFactory.isClosed()) {
            sessionFactory.close();
        }
    }
}
