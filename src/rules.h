/* The rules definitions.

   Copyright (C) 2012, 2013, 2014 Ian Dunn.

   This program is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

#ifndef ARIS_RULES_H
#define ARIS_RULES_H

#ifndef WIN32
#include <libintl.h>
#include <wchar.h>
#define _(String) gettext (String)
#define gettext_noop(String) String
#define N_(String) gettext_noop (String)
#else
#define _(String) String
#define N_(String) String
#endif

// The rules indices.

enum RULES_INDEX {
  RULE_CN = 0, RULE_SM,
  RULE_AD, RULE_DS,
  RULE_MP, RULE_HS,
  RULE_EX, RULE_CD,
  RULE_IM, RULE_ID,
  RULE_DM, RULE_DT,
  RULE_AS, RULE_CO,
  RULE_EQ, RULE_EP,
  RULE_DN, RULE_SB,
  RULE_UG, RULE_UI,
  RULE_EG, RULE_EI,
  RULE_BV, RULE_FV,
  RULE_NQ, RULE_PR,
  RULE_II,
  RULE_LM, RULE_SP,
  RULE_SQ, RULE_IN,
  RULE_BI, RULE_BD,
  RULE_BN, RULE_SN,
  NUM_RULES
};

#define END_INFER_RULES RULE_IM
#define END_EQUIV_RULES RULE_UG
#define END_PRED_RULES RULE_LM
#define END_MISC_RULES RULE_BI
#define END_BOOL_RULES NUM_RULES

// The rules list.

//when changed must also change the corresponding values in sexpr-process-*.c
static const wchar_t* rules_list[NUM_RULES] = {
  (wchar_t*) "Conjunction", (wchar_t*) "Simplification",
  (wchar_t*) "Addition", (wchar_t*) "Disjunctive Syllogism",
  (wchar_t*) "Modus Ponens", (wchar_t*) "Hypothetical Syllogism",
  (wchar_t*) "Excluded Middle", (wchar_t*) "Constructive Dilemma",
  (wchar_t*) "Implication", (wchar_t*) "Idempotence",
  (wchar_t*) "DeMorgan", (wchar_t*) "Distribution",
  (wchar_t*) "Association", (wchar_t*) "Commutativity",
  (wchar_t*) "Equivalence", (wchar_t*) "Exportation",
  (wchar_t*) "Double Negation", (wchar_t*) "Subsumption",
  (wchar_t*) "\u2200 Intro", (wchar_t*) "\u2200 Elim",
  (wchar_t*) "\u2203 Intro", (wchar_t*) "\u2203 Elim",
  (wchar_t*) "Bound Variable Sub.", (wchar_t*) "Free Variable Sub.",
  (wchar_t*) "Null Quantifier", (wchar_t*) "Prenex", 
  (wchar_t*) "Identity",
  (wchar_t*) "Lemma", (wchar_t*) "Subproof",
  (wchar_t*) "Sequence", (wchar_t*) "Induction",
  (wchar_t*) "Boolean Identity", (wchar_t*) "Boolean Dominance",
  (wchar_t*) "Boolean Negation", (wchar_t*) "Symbol Negation"
};

static char* rules_names[NUM_RULES] = {
  N_("Conjunction"), N_("Simplification"),
  N_("Addition"), N_("Disjunctive Syllogism"),
  N_("Modus Ponens"), N_("Hypothetical Syllogism"),
  N_("Excluded Middle"), N_("Constructive Dilemma"),
  N_("Implication"), N_("Idempotence"),
  N_("DeMorgan"), N_("Distribution"),
  N_("Association"), N_("Commutativity"),
  N_("Equivalence"), N_("Exportation"),
  N_("Double Negation"), N_("Subsumption"),
  N_("Universal Generalization"), N_("Universal Instantiation"),
  N_("Existential Generalization"), N_("Existential Instantiation"),
  N_("Bound Variable Substitution"), N_("Free Variable Substitution"),
  N_("Null Quantifier"), N_("Prenex"),
  N_("Identity"),
  N_("Lemma"), N_("Subproof"),
  N_("Sequence"), N_("Induction"),
  N_("Boolean Identity"), N_("Boolean Dominance"),
  N_("Boolean Negation"), N_("Symbol Negation")
};

#endif  /*  ARIS_RULES_H  */
