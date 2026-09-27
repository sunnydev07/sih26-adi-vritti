from app.services import gap_service, matching_service


def test_transliteration_scores_high():
    a = {"full_name": "Sunita Meena", "dob": "2012-05-01", "gender": "female",
         "guardian_name": "Soma Ram", "institution_code": "SCH-0001",
         "district": "Mandla", "bank_account_last4": "1234"}
    b = dict(a, full_name="Sunita Mina")
    total, _ = matching_service.score(a, b)
    assert total > 0.9


def test_different_people_score_low():
    a = {"full_name": "Sunita Meena", "dob": "2012-05-01", "gender": "female",
         "guardian_name": "Soma Ram", "institution_code": "SCH-0001",
         "district": "Mandla", "bank_account_last4": "1234"}
    b = {"full_name": "Birsa Munda", "dob": "2009-11-20", "gender": "male",
         "guardian_name": "Deva Ram", "institution_code": "SCH-0099",
         "district": "Gumla", "bank_account_last4": "9876"}
    total, _ = matching_service.score(a, b)
    assert total < 0.6


def test_hmac_join_is_left_anti():
    assert gap_service.left_anti_join({"a", "b"}, {"b"}) == {"a"}


def test_hmac_is_deterministic_and_salted():
    h1 = gap_service.hashed_key("AVR-x", "salt-1")
    assert h1 == gap_service.hashed_key("AVR-x", "salt-1")
    assert h1 != gap_service.hashed_key("AVR-x", "salt-2")
