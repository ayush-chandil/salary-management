package com.acme.salary.seed;

/**
 * Fixed name pools for the seed.
 *
 * <p>A faker library would be the obvious choice, but a fixed pool keeps the seed dependency-free
 * and makes determinism trivial to reason about: the same random seed always produces the same
 * names, with no library version in the way.
 *
 * <p>~80 x ~80 gives ~6,400 combinations for 10,000 employees, so duplicate full names occur --
 * which is realistic, and worth exercising in the directory's search results.
 */
final class NameCatalog {

  private NameCatalog() {}

  static final String[] FIRST_NAMES = {
    "Aditi", "Alejandro", "Amara", "Ana", "Andreas", "Anika", "Arjun", "Ayesha",
    "Beatriz", "Bruno", "Camila", "Carlos", "Chen", "Chloe", "Daniel", "Diego",
    "Elena", "Elias", "Emeka", "Emma", "Fatima", "Felix", "Freya", "Gabriel",
    "Hana", "Hannah", "Haruto", "Hugo", "Ines", "Isabel", "Ishaan", "Ivan",
    "Jasmin", "Javier", "Jonas", "Julia", "Kaito", "Kavya", "Kenji", "Klara",
    "Lars", "Laura", "Leon", "Lucia", "Mateo", "Maya", "Mei", "Miguel",
    "Nadia", "Naoki", "Neha", "Niklas", "Nora", "Olivia", "Omar", "Oscar",
    "Pablo", "Priya", "Rafael", "Rahul", "Ravi", "Rosa", "Ruben", "Sakura",
    "Sanjay", "Sara", "Sofia", "Sophie", "Takeshi", "Tara", "Thomas", "Tobias",
    "Valentina", "Vikram", "Wei", "Yuki", "Yusuf", "Zara", "Zoe", "Anton"
  };

  static final String[] LAST_NAMES = {
    "Almeida", "Andersson", "Bauer", "Becker", "Bianchi", "Braun", "Carvalho", "Castro",
    "Chandra", "Chen", "Costa", "Dias", "Dubois", "Fernandes", "Fischer", "Fujimoto",
    "Garcia", "Gomes", "Gupta", "Hansen", "Hoffmann", "Ibrahim", "Iyer", "Jensen",
    "Kaur", "Keller", "Khan", "Kimura", "Klein", "Koch", "Kumar", "Lima",
    "Lopez", "Maier", "Martin", "Mehta", "Mendes", "Meyer", "Moreau", "Muller",
    "Nakamura", "Navarro", "Nair", "Okafor", "Oliveira", "Ortiz", "Patel", "Pereira",
    "Petersen", "Ramos", "Reddy", "Ribeiro", "Richter", "Rossi", "Ruiz", "Santos",
    "Sato", "Schmidt", "Schneider", "Sharma", "Silva", "Singh", "Sousa", "Suzuki",
    "Tanaka", "Torres", "Vargas", "Verma", "Wagner", "Watanabe", "Weber", "Wolf",
    "Yamamoto", "Yoshida", "Zhang", "Zimmermann", "Novak", "Horvath", "Kowalski", "Nowak"
  };

  /** Role nouns per department code, combined with the level name for a job title. */
  static String roleFor(String departmentCode) {
    return switch (departmentCode) {
      case "ENG" -> "Engineer";
      case "PROD" -> "Product Manager";
      case "DES" -> "Designer";
      case "SALES" -> "Account Executive";
      case "MKT" -> "Marketing Manager";
      case "CS" -> "Customer Success Manager";
      case "FIN" -> "Financial Analyst";
      case "HR" -> "People Partner";
      case "LEGAL" -> "Counsel";
      case "OPS" -> "Operations Manager";
      default -> "Specialist";
    };
  }
}
