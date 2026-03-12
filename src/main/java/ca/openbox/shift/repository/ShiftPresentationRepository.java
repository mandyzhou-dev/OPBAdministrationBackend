package ca.openbox.shift.repository;

import ca.openbox.shift.presentation.ShiftPresentation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public interface ShiftPresentationRepository extends JpaRepository<ShiftPresentation, Integer> {

    @Query( value = "select id, opb_user.username as username, start as date, name as user_real_name, " +
            "opb_shift_arrangement.group_name, start, end from opb_shift_arrangement\n"+
            "left join opb_user on opb_shift_arrangement.username = opb_user.username\n" +
            "where (start>=:start and start <=:end) \n" +
            "and opb_shift_arrangement.status in (\"active\",\"cancelled\") \n"+
            "and opb_shift_arrangement.username =:username",
            nativeQuery = true)
    public Collection<ShiftPresentation> getSchedulePresentationByUsernameAndStartdate(String username,ZonedDateTime start, ZonedDateTime end);

    @Query( value = "select id, opb_user.username as username, start as date, name as user_real_name, " +
            "opb_shift_arrangement.group_name, start, end from opb_shift_arrangement\n"+
            "left join opb_user on opb_shift_arrangement.username = opb_user.username\n" +
            "where (start>=:start and start <=:end) \n" +
            "and opb_shift_arrangement.status in (\"active\",\"cancelled\")",
            nativeQuery = true)
    public List<ShiftPresentation> getByTimeScope(ZonedDateTime start, ZonedDateTime end);

    @Query( value = "select id, opb_user.username as username, start as date, name as user_real_name, " +
            "opb_shift_arrangement.group_name, start, end from opb_shift_arrangement\n"+
            "left join opb_user on opb_shift_arrangement.username = opb_user.username\n" +
            "where (start>=:start and start <=:end) \n" +
            "and opb_shift_arrangement.status in (\"active\",\"cancelled\")"+
            "and opb_shift_arrangement.group_name=:groupName",
            nativeQuery = true)
    public Collection<ShiftPresentation> getByGroupAndTimeScope(String groupName, ZonedDateTime start, ZonedDateTime end);

    @Query(value = "select opb_shift_arrangement.id, opb_user.username as username, start as date, " +
            "name as user_real_name, opb_shift_arrangement.group_name, start, end " +
            "from opb_shift_arrangement\n" +
            "left join opb_user on opb_shift_arrangement.username = opb_user.username\n" +
            "where (start >= :start and start <= :end) \n" +
            "and opb_shift_arrangement.status in ('active', 'cancelled') \n" +
            "and (opb_shift_arrangement.group_name = :groupName OR opb_shift_arrangement.username = :username)",
            nativeQuery = true)
    public List<ShiftPresentation> getByGroupOrUsernameBetween(String groupName, String username,
                                                                                                    ZonedDateTime start, ZonedDateTime end);

}
